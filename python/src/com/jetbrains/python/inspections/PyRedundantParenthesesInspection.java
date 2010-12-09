package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.actions.RedundantParenthesesQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User: catherine
 *
 * Inspection to detect redundant parentheses in if/while statement.
 */
public class PyRedundantParenthesesInspection extends PyInspection {

  public boolean myIgnorePercOperator = false;
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.redundant.parentheses");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder, myIgnorePercOperator);
  }

  private static class Visitor extends PyInspectionVisitor {
    private final boolean myIgnorePercOperator;
    public Visitor(final ProblemsHolder holder, boolean ignorePercOperator) {
      super(holder);
      myIgnorePercOperator = ignorePercOperator;
    }

    @Override
    public void visitPyParenthesizedExpression(final PyParenthesizedExpression node) {
      if (node.getContainedExpression() instanceof PyReferenceExpression ||
            node.getContainedExpression() instanceof PyStringLiteralExpression
              || node.getContainedExpression() instanceof PyNumericLiteralExpression) {
        if (myIgnorePercOperator) {
          PsiElement parent = node.getParent();
          if (parent instanceof PyBinaryExpression) {
            if (((PyBinaryExpression)parent).getOperator() == PyTokenTypes.PERC) return;
          }
        }
        
        if (node.getParent() instanceof PyPrintStatement)
          return;
        registerProblem(node, "Remove redundant parentheses", new RedundantParenthesesQuickFix());
      }
      else if (node.getParent() instanceof PyIfPart || node.getParent() instanceof PyWhilePart
                  || node.getParent() instanceof PyReturnStatement) {
        if (!node.getText().contains("\n")) {
          
          registerProblem(node, "Remove redundant parentheses", new RedundantParenthesesQuickFix());
        }
      }

    }
  }
  
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Ignore argument of % operator", "myIgnorePercOperator");
    return panel;
  }
}
