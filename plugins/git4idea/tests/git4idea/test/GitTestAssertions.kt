/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package git4idea.test

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil.getFilePath
import git4idea.repo.GitRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File

fun GitRepository.assertStatus(file: VirtualFile, status: Char) {
  this.assertStatus(getFilePath(file), status)
}

fun GitRepository.assertStatus(file: FilePath, status: Char) {
  this.assertStatus(file.ioFile, status)
}

fun GitRepository.assertStatus(file: File, status: Char) {
  val actualStatus = git(this, "status --porcelain ${file.path}")
  assertTrue("File status is not-changed: $actualStatus", !actualStatus.isEmpty())
  assertEquals("File status is incorrect: $actualStatus", status, actualStatus[0])
}