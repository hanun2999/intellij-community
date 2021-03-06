// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.workspaceModel.storage.entities.*
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageImpl
import com.intellij.workspaceModel.storage.impl.exceptions.AddDiffException
import com.intellij.workspaceModel.storage.impl.external.ExternalEntityMappingImpl
import org.hamcrest.CoreMatchers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

private fun WorkspaceEntityStorageBuilder.applyDiff(anotherBuilder: WorkspaceEntityStorageBuilder): WorkspaceEntityStorage {
  val builder = WorkspaceEntityStorageBuilderImpl.from(this)
  builder.addDiff(anotherBuilder)
  val storage =  builder.toStorage()
  storage.assertConsistency()
  return storage
}

class DiffBuilderTest {
  @JvmField
  @Rule
  val expectedException = ExpectedException.none()

  @Test
  fun `add entity`() {
    val source = WorkspaceEntityStorageBuilderImpl.create()
    source.addSampleEntity("first")
    val target = WorkspaceEntityStorageBuilderImpl.create()
    target.addSampleEntity("second")
    val storage = target.applyDiff(source)
    assertEquals(setOf("first", "second"), storage.entities(SampleEntity::class.java).mapTo(HashSet()) { it.stringProperty })
  }

  @Test
  fun `remove entity`() {
    val target = WorkspaceEntityStorageBuilderImpl.create()
    val entity = target.addSampleEntity("hello")
    val entity2 = target.addSampleEntity("hello")
    val source = WorkspaceEntityStorageBuilderImpl.from(target.toStorage())
    source.removeEntity(entity)
    val storage = target.applyDiff(source)
    assertEquals(entity2, storage.singleSampleEntity())
  }

  @Test
  fun `modify entity`() {
    val target = WorkspaceEntityStorageBuilderImpl.create()
    val entity = target.addSampleEntity("hello")
    val source = WorkspaceEntityStorageBuilderImpl.from(target.toStorage())
    source.modifyEntity(ModifiableSampleEntity::class.java, entity) {
      stringProperty = "changed"
    }
    val storage = target.applyDiff(source)
    assertEquals("changed", storage.singleSampleEntity().stringProperty)
  }

  @Test
  fun `remove removed entity`() {
    val target = WorkspaceEntityStorageBuilderImpl.create()
    val entity = target.addSampleEntity("hello")
    val entity2 = target.addSampleEntity("hello")
    val source = WorkspaceEntityStorageBuilderImpl.from(target.toStorage())
    target.removeEntity(entity)
    target.assertConsistency()
    source.assertConsistency()
    source.removeEntity(entity)
    val storage = target.applyDiff(source)
    assertEquals(entity2, storage.singleSampleEntity())
  }

  @Test
  fun `modify removed entity`() {
    val target = WorkspaceEntityStorageBuilderImpl.create()
    val entity = target.addSampleEntity("hello")
    val source = WorkspaceEntityStorageBuilderImpl.from(target.toStorage())
    target.removeEntity(entity)
    source.assertConsistency()
    source.modifyEntity(ModifiableSampleEntity::class.java, entity) {
      stringProperty = "changed"
    }
    val storage = target.applyDiff(source)
    assertEquals(emptyList<SampleEntity>(), storage.entities(SampleEntity::class.java).toList())
  }

  @Test
  fun `modify removed child entity`() {
    val target = WorkspaceEntityStorageBuilderImpl.create()
    val parent = target.addParentEntity("parent")
    val child = target.addChildEntity(parent, "child")
    val source = WorkspaceEntityStorageBuilderImpl.from(target)
    target.removeEntity(child)
    source.modifyEntity(ModifiableParentEntity::class.java, parent) {
      this.parentProperty = "new property"
    }
    source.modifyEntity(ModifiableChildEntity::class.java, child) {
      this.childProperty = "new property"
    }

    val res = target.applyDiff(source) as WorkspaceEntityStorageImpl
    res.assertConsistency()

    assertOneElement(res.entities(ParentEntity::class.java).toList())
    assertTrue(res.entities(ChildEntity::class.java).toList().isEmpty())
  }

  @Test
  fun `remove modified entity`() {
    val target = WorkspaceEntityStorageBuilderImpl.create()
    val entity = target.addSampleEntity("hello")
    val source = WorkspaceEntityStorageBuilderImpl.from(target.toStorage())
    target.modifyEntity(ModifiableSampleEntity::class.java, entity) {
      stringProperty = "changed"
    }
    source.removeEntity(entity)
    source.assertConsistency()
    val storage = target.applyDiff(source)
    assertEquals(emptyList<SampleEntity>(), storage.entities(SampleEntity::class.java).toList())
  }

  @Test
  fun `add entity with refs at the same slot`() {
    val target = WorkspaceEntityStorageBuilderImpl.create()
    val source = WorkspaceEntityStorageBuilderImpl.create()
    source.addSampleEntity("Another entity")
    val parentEntity = target.addSampleEntity("hello")
    target.addChildSampleEntity("data", parentEntity)

    source.addDiff(target)
    source.assertConsistency()

    val resultingStorage = source.toStorage()
    assertEquals(2, resultingStorage.entities(SampleEntity::class.java).toList().size)
    assertEquals(1, resultingStorage.entities(ChildSampleEntity::class.java).toList().size)

    assertEquals(resultingStorage.entities(SampleEntity::class.java).last(), resultingStorage.entities(ChildSampleEntity::class.java).single().parent)
  }

  @Test
  fun `add remove and add with refs`() {
    val source = WorkspaceEntityStorageBuilderImpl.create()
    val target = WorkspaceEntityStorageBuilderImpl.create()
    val parent = source.addSampleEntity("Another entity")
    source.addChildSampleEntity("String", parent)

    val parentEntity = target.addSampleEntity("hello")
    target.addChildSampleEntity("data", parentEntity)

    source.addDiff(target)
    source.assertConsistency()

    val resultingStorage = source.toStorage()
    assertEquals(2, resultingStorage.entities(SampleEntity::class.java).toList().size)
    assertEquals(2, resultingStorage.entities(ChildSampleEntity::class.java).toList().size)

    assertNotNull(resultingStorage.entities(ChildSampleEntity::class.java).first().parent)
    assertNotNull(resultingStorage.entities(ChildSampleEntity::class.java).last().parent)

    assertEquals(resultingStorage.entities(SampleEntity::class.java).first(), resultingStorage.entities(ChildSampleEntity::class.java).first().parent)
    assertEquals(resultingStorage.entities(SampleEntity::class.java).last(), resultingStorage.entities(ChildSampleEntity::class.java).last().parent)
  }

  @Test
  fun `add dependency without changing entities`() {
    val source = WorkspaceEntityStorageBuilderImpl.create()
    val parent = source.addSampleEntity("Another entity")
    source.addChildSampleEntity("String", null)

    val target = WorkspaceEntityStorageBuilderImpl.from(source)
    val pchild = target.entities(ChildSampleEntity::class.java).single()
    val pparent = target.entities(SampleEntity::class.java).single()
    target.modifyEntity(ModifiableChildSampleEntity::class.java, pchild) {
      this.parent = pparent
    }

    source.addDiff(target)
    source.assertConsistency()

    val resultingStorage = source.toStorage()
    assertEquals(1, resultingStorage.entities(SampleEntity::class.java).toList().size)
    assertEquals(1, resultingStorage.entities(ChildSampleEntity::class.java).toList().size)

    assertEquals(resultingStorage.entities(SampleEntity::class.java).single(), resultingStorage.entities(ChildSampleEntity::class.java).single().parent)
  }

  @Test
  fun `dependency to removed parent`() {
    val source = WorkspaceEntityStorageBuilderImpl.create()
    val parent = source.addParentEntity()

    val target = WorkspaceEntityStorageBuilderImpl.from(source)
    target.addChildWithOptionalParentEntity(parent)
    source.removeEntity(parent)

    source.applyDiff(target)
  }

  @Test
  fun `modify child and parent`() {
    val source = WorkspaceEntityStorageBuilderImpl.create()
    val parent = source.addParentEntity()
    source.addChildEntity(parent)

    val target = WorkspaceEntityStorageBuilderImpl.from(source)
    target.modifyEntity(ModifiableParentEntity::class.java, parent) {
      this.parentProperty = "anotherValue"
    }
    source.addChildEntity(parent)

    source.applyDiff(target)
  }

  @Test
  fun `remove parent in both difs with dependency`() {
    val source = WorkspaceEntityStorageBuilderImpl.create()
    val parent = source.addParentEntity()

    val target = WorkspaceEntityStorageBuilderImpl.from(source)
    target.addChildWithOptionalParentEntity(parent)
    target.removeEntity(parent)
    source.removeEntity(parent)

    source.applyDiff(target)
  }

  @Test
  fun `remove parent in both diffs`() {
    val source = WorkspaceEntityStorageBuilderImpl.create()
    val parent = source.addParentEntity()
    val optionalChild = source.addChildWithOptionalParentEntity(null)

    val target = WorkspaceEntityStorageBuilderImpl.from(source)
    target.modifyEntity(ModifiableChildWithOptionalParentEntity::class.java, optionalChild) {
      this.optionalParent = parent
    }

    source.removeEntity(parent)

    source.applyDiff(target)
  }

  @Test
  fun `adding duplicated persistent ids`() {
    expectedException.expectCause(CoreMatchers.isA(AddDiffException::class.java))

    val source = WorkspaceEntityStorageBuilderImpl.create()
    val target = WorkspaceEntityStorageBuilderImpl.from(source)

    target.addNamedEntity("Name")
    source.addNamedEntity("Name")

    source.applyDiff(target)
  }

  @Test
  fun `modifying duplicated persistent ids`() {
    expectedException.expectCause(CoreMatchers.isA(AddDiffException::class.java))

    val source = WorkspaceEntityStorageBuilderImpl.create()
    val namedEntity = source.addNamedEntity("Hello")
    val target = WorkspaceEntityStorageBuilderImpl.from(source)

    source.addNamedEntity("Name")
    target.modifyEntity(ModifiableNamedEntity::class.java, namedEntity) {
      this.name = "Name"
    }

    source.applyDiff(target)
  }

  @Test
  fun `checking external mapping`() {
    val target = WorkspaceEntityStorageBuilderImpl.create()

    target.addSampleEntity("Entity at index 0")

    val source = WorkspaceEntityStorageBuilderImpl.create()
    val sourceSample = source.addSampleEntity("Entity at index 1")
    val mutableExternalMapping = source.getMutableExternalMapping<Any>("Ext")
    val anyObj = Any()
    mutableExternalMapping.addMapping(sourceSample, anyObj)

    target.addDiff(source)

    val externalMapping = target.getExternalMapping<Any>("Ext") as ExternalEntityMappingImpl<Any>
    assertEquals(1, externalMapping.index.size)
  }

  @Test
  fun `change source in diff`() {
    val target = WorkspaceEntityStorageBuilderImpl.create()
    val sampleEntity = target.addSampleEntity("Prop", MySource)

    val source = WorkspaceEntityStorageBuilder.from(target)
    source.changeSource(sampleEntity, AnotherSource)

    target.addDiff(source)

    target.assertConsistency()

    val entitySourceIndex = target.indexes.entitySourceIndex
    assertEquals(1, entitySourceIndex.index.size)
    assertNotNull(entitySourceIndex.getIdsByEntry(AnotherSource)?.single())
  }

  @Test
  fun `change source in target`() {
    val target = WorkspaceEntityStorageBuilderImpl.create()
    val sampleEntity = target.addSampleEntity("Prop", MySource)

    val source = WorkspaceEntityStorageBuilder.from(target)
    target.changeSource(sampleEntity, AnotherSource)

    source.modifyEntity(ModifiableSampleEntity::class.java, sampleEntity) {
      this.stringProperty = "Updated"
    }

    target.addDiff(source)

    target.assertConsistency()

    val entitySourceIndex = target.indexes.entitySourceIndex
    assertEquals(1, entitySourceIndex.index.size)
    assertNotNull(entitySourceIndex.getIdsByEntry(AnotherSource)?.single())
  }

  @Test
  fun `adding parent with child and shifting`() {
    val parentAndChildProperty = "Bound"
    val target = WorkspaceEntityStorageBuilderImpl.create()
    target.addChildWithOptionalParentEntity(null, "Existing")

    val source = WorkspaceEntityStorageBuilderImpl.create()
    val parent = source.addParentEntity(parentAndChildProperty)
    source.addChildWithOptionalParentEntity(parent, parentAndChildProperty)

    target.addDiff(source)

    val extractedParent = assertOneElement(target.entities(ParentEntity::class.java).toList())
    val extractedOptionalChild = assertOneElement(extractedParent.optionalChildren.toList())
    assertEquals(parentAndChildProperty, extractedOptionalChild.childProperty)
  }

  @Test
  fun `adding parent with child and shifting and later connecting`() {
    val parentAndChildProperty = "Bound"
    val target = WorkspaceEntityStorageBuilderImpl.create()
    target.addChildWithOptionalParentEntity(null, "Existing")

    val source = WorkspaceEntityStorageBuilderImpl.create()
    val child = source.addChildWithOptionalParentEntity(null, parentAndChildProperty)
    val parent = source.addParentEntity(parentAndChildProperty)
    source.modifyEntity(ModifiableParentEntity::class.java, parent) {
      this.optionalChildren = this.optionalChildren + child
    }

    target.addDiff(source)

    val extractedParent = assertOneElement(target.entities(ParentEntity::class.java).toList())
    val extractedOptionalChild = assertOneElement(extractedParent.optionalChildren.toList())
    assertEquals(parentAndChildProperty, extractedOptionalChild.childProperty)
  }

  @Test
  fun `adding parent with child and shifting and later child connecting`() {
    val parentAndChildProperty = "Bound"
    val target = WorkspaceEntityStorageBuilderImpl.create()
    target.addChildWithOptionalParentEntity(null, "Existing")

    val source = WorkspaceEntityStorageBuilderImpl.create()
    val child = source.addChildWithOptionalParentEntity(null, parentAndChildProperty)
    val parent = source.addParentEntity(parentAndChildProperty)
    source.modifyEntity(ModifiableChildWithOptionalParentEntity::class.java, child) {
      this.optionalParent = parent
    }

    target.addDiff(source)

    val extractedParent = assertOneElement(target.entities(ParentEntity::class.java).toList())
    val extractedOptionalChild = assertOneElement(extractedParent.optionalChildren.toList())
    assertEquals(parentAndChildProperty, extractedOptionalChild.childProperty)
  }
}
