/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.*;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * A type of item with a distinct highlighting in an editor or in other views.
 */
public final class TextAttributesKey implements Comparable<TextAttributesKey>, JDOMExternalizable {
  private static final TextAttributes NULL_ATTRIBUTES = new TextAttributes();
  private static final ConcurrentHashMap<String, TextAttributesKey> ourRegistry = new ConcurrentHashMap<String, TextAttributesKey>();
  private static final NullableLazyValue<TextAttributeKeyDefaultsProvider> ourDefaultsProvider = new VolatileNullableLazyValue<TextAttributeKeyDefaultsProvider>() {
    @Nullable
    @Override
    protected TextAttributeKeyDefaultsProvider compute() {
      return ServiceManager.getService(TextAttributeKeyDefaultsProvider.class);
    }
  };

  public String myExternalName;
  public TextAttributes myDefaultAttributes = NULL_ATTRIBUTES;

  private TextAttributesKey myFallbackAttributeKey;

  private TextAttributesKey(String externalName) {
    myExternalName = externalName;
  }

  //read external only
  public TextAttributesKey() {
  }

  @NotNull public static TextAttributesKey find(@NotNull @NonNls String externalName) {
    return ourRegistry.cacheOrGet(externalName, new TextAttributesKey(externalName));
  }

  public String toString() {
    return myExternalName;
  }

  public String getExternalName() {
    return myExternalName;
  }

  public int compareTo(TextAttributesKey key) {
    return myExternalName.compareTo(key.myExternalName);
  }

  /**
   * Registers a text attribute key with the specified identifier.
   *
   * @param externalName      the unique identifier of the key.
   * @return the new key instance, or an existing instance if the key with the same
   *         identifier was already registered.
   */
  @NotNull public static TextAttributesKey createTextAttributesKey(@NonNls @NotNull String externalName) {
    return find(externalName);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }


  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final TextAttributesKey that = (TextAttributesKey)o;

    if (!myExternalName.equals(that.myExternalName)) return false;

    return true;
  }

  public int hashCode() {
    return myExternalName.hashCode();
  }

  /**
   * Returns the default text attributes associated with the key.
   *
   * @return the text attributes.
   */
  public TextAttributes getDefaultAttributes() {
    if (myDefaultAttributes == NULL_ATTRIBUTES) {
      // E.g. if one text key reuse default attributes of some other predefined key
      myDefaultAttributes = null;
      final TextAttributeKeyDefaultsProvider provider = ourDefaultsProvider.getValue();
      if (provider != null)
        myDefaultAttributes = provider.getDefaultAttributes(this);
    }
    return myDefaultAttributes;
  }

  /**
   * Registers a text attribute key with the specified identifier and default attributes.
   *
   * @param externalName      the unique identifier of the key.
   * @param defaultAttributes the default text attributes associated with the key.
   * @return the new key instance, or an existing instance if the key with the same
   *         identifier was already registered.
   */
  @NotNull
  public static TextAttributesKey createTextAttributesKey(@NonNls @NotNull String externalName, TextAttributes defaultAttributes) {
    TextAttributesKey key = find(externalName);
    if (key.myDefaultAttributes == null || key.myDefaultAttributes == NULL_ATTRIBUTES) {
      key.myDefaultAttributes = defaultAttributes;
    }
    return key;
  }

  @NotNull
  public static TextAttributesKey createTextAttributesKey(@NonNls @NotNull String externalName, TextAttributesKey fallbackAttributeKey) {
    TextAttributesKey key = find(externalName);
    key.setFallbackAttributeKey(fallbackAttributeKey);
    return key;
  }

  public TextAttributesKey getFallbackAttributeKey() {
    return myFallbackAttributeKey;
  }

  public void setFallbackAttributeKey(TextAttributesKey fallbackAttributeKey) {
    myFallbackAttributeKey = fallbackAttributeKey;
  }

  public interface TextAttributeKeyDefaultsProvider {
    TextAttributes getDefaultAttributes(TextAttributesKey key);
  }
}
