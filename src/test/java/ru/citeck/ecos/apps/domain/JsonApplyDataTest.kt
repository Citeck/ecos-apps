package ru.citeck.ecos.apps.domain

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import ru.citeck.ecos.apps.domain.ecosapp.api.records.EcosAppRecords
import ru.citeck.ecos.apps.domain.ecosapp.dto.EcosAppDef
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json

class JsonApplyDataTest {

    @Test
    fun test() {
        val record = EcosAppRecords.EcosAppRecord(EcosAppDef.create().build(), Mockito.mock(), Mockito.mock(), Mockito.mock())
        Json.mapper.applyData(record, ObjectData.create())
    }

/*
Error with parent 3.17.8
After update to 3.18.10 everything ok

kotlin.reflect.jvm.internal.KotlinReflectionInternalError: Unknown origin of public abstract operator fun invoke(p1: P1): R defined in kotlin.Function1[FunctionInvokeDescriptor@261275ae] (class kotlin.reflect.jvm.internal.impl.builtins.functions.FunctionInvokeDescriptor)

	at kotlin.reflect.jvm.internal.RuntimeTypeMapper.mapSignature(RuntimeTypeMapper.kt:226)
	at kotlin.reflect.jvm.internal.KFunctionImpl.<init>(KFunctionImpl.kt:55)
	at kotlin.reflect.jvm.internal.CreateKCallableVisitor.visitFunctionDescriptor(util.kt:314)
	at kotlin.reflect.jvm.internal.CreateKCallableVisitor.visitFunctionDescriptor(util.kt:291)
	at kotlin.reflect.jvm.internal.impl.descriptors.impl.FunctionDescriptorImpl.accept(FunctionDescriptorImpl.java:826)
	at kotlin.reflect.jvm.internal.KDeclarationContainerImpl.getMembers(KDeclarationContainerImpl.kt:62)
	at kotlin.reflect.jvm.internal.KClassImpl$Data.declaredNonStaticMembers_delegate$lambda$22(KClassImpl.kt:173)
	at kotlin.reflect.jvm.internal.KClassImpl$Data.accessor$KClassImpl$Data$lambda10(KClassImpl.kt)
	at kotlin.reflect.jvm.internal.KClassImpl$Data$$Lambda$10.invoke(Unknown Source)
	at kotlin.reflect.jvm.internal.ReflectProperties$LazySoftVal.invoke(ReflectProperties.java:70)
	at kotlin.reflect.jvm.internal.ReflectProperties$Val.getValue(ReflectProperties.java:32)
	at kotlin.reflect.jvm.internal.KClassImpl$Data.getDeclaredNonStaticMembers(KClassImpl.kt:173)
	at kotlin.reflect.jvm.internal.KClassImpl$Data.allNonStaticMembers_delegate$lambda$26(KClassImpl.kt:182)
	at kotlin.reflect.jvm.internal.KClassImpl$Data.accessor$KClassImpl$Data$lambda14(KClassImpl.kt)
	at kotlin.reflect.jvm.internal.KClassImpl$Data$$Lambda$14.invoke(Unknown Source)
	at kotlin.reflect.jvm.internal.ReflectProperties$LazySoftVal.invoke(ReflectProperties.java:70)
	at kotlin.reflect.jvm.internal.ReflectProperties$Val.getValue(ReflectProperties.java:32)
	at kotlin.reflect.jvm.internal.KClassImpl$Data.getAllNonStaticMembers(KClassImpl.kt:182)
	at kotlin.reflect.full.KClasses.getMemberProperties(KClasses.kt:148)
	at com.fasterxml.jackson.module.kotlin.KotlinNamesAnnotationIntrospector.findDefaultCreator(KotlinNamesAnnotationIntrospector.kt:99)
	at com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair.findDefaultCreator(AnnotationIntrospectorPair.java:746)
	at com.fasterxml.jackson.databind.introspect.POJOPropertiesCollector._addCreators(POJOPropertiesCollector.java:669)
	at com.fasterxml.jackson.databind.introspect.POJOPropertiesCollector.collectAll(POJOPropertiesCollector.java:451)
	at com.fasterxml.jackson.databind.introspect.POJOPropertiesCollector.getPotentialCreators(POJOPropertiesCollector.java:229)
	at com.fasterxml.jackson.databind.introspect.BasicBeanDescription.getPotentialCreators(BasicBeanDescription.java:348)
	at com.fasterxml.jackson.databind.deser.BasicDeserializerFactory._constructDefaultValueInstantiator(BasicDeserializerFactory.java:251)
	at com.fasterxml.jackson.databind.deser.BasicDeserializerFactory.findValueInstantiator(BasicDeserializerFactory.java:219)
	at com.fasterxml.jackson.databind.deser.BeanDeserializerFactory.buildBeanDeserializer(BeanDeserializerFactory.java:262)
	at com.fasterxml.jackson.databind.deser.BeanDeserializerFactory.createBeanDeserializer(BeanDeserializerFactory.java:151)
	at com.fasterxml.jackson.databind.deser.DeserializerCache._createDeserializer2(DeserializerCache.java:471)
	at com.fasterxml.jackson.databind.deser.DeserializerCache._createDeserializer(DeserializerCache.java:415)
	at com.fasterxml.jackson.databind.deser.DeserializerCache._createAndCache2(DeserializerCache.java:317)
	at com.fasterxml.jackson.databind.deser.DeserializerCache._createAndCacheValueDeserializer(DeserializerCache.java:284)
	at com.fasterxml.jackson.databind.deser.DeserializerCache.findValueDeserializer(DeserializerCache.java:174)
	at com.fasterxml.jackson.databind.DeserializationContext.findNonContextualValueDeserializer(DeserializationContext.java:659)
	at com.fasterxml.jackson.databind.deser.BeanDeserializerBase.resolve(BeanDeserializerBase.java:552)
	at com.fasterxml.jackson.databind.deser.DeserializerCache._createAndCache2(DeserializerCache.java:347)
	at com.fasterxml.jackson.databind.deser.DeserializerCache._createAndCacheValueDeserializer(DeserializerCache.java:284)
	at com.fasterxml.jackson.databind.deser.DeserializerCache.findValueDeserializer(DeserializerCache.java:174)
	at com.fasterxml.jackson.databind.DeserializationContext.findNonContextualValueDeserializer(DeserializationContext.java:659)
	at com.fasterxml.jackson.databind.deser.BeanDeserializerBase.resolve(BeanDeserializerBase.java:552)
	at com.fasterxml.jackson.databind.deser.DeserializerCache._createAndCache2(DeserializerCache.java:347)
	at com.fasterxml.jackson.databind.deser.DeserializerCache._createAndCacheValueDeserializer(DeserializerCache.java:284)
	at com.fasterxml.jackson.databind.deser.DeserializerCache.findValueDeserializer(DeserializerCache.java:174)
	at com.fasterxml.jackson.databind.DeserializationContext.findNonContextualValueDeserializer(DeserializationContext.java:659)
	at com.fasterxml.jackson.databind.deser.BeanDeserializerBase.resolve(BeanDeserializerBase.java:552)
	at com.fasterxml.jackson.databind.deser.DeserializerCache._createAndCache2(DeserializerCache.java:347)
	at com.fasterxml.jackson.databind.deser.DeserializerCache._createAndCacheValueDeserializer(DeserializerCache.java:284)
	at com.fasterxml.jackson.databind.deser.DeserializerCache.findValueDeserializer(DeserializerCache.java:174)
	at com.fasterxml.jackson.databind.DeserializationContext.findRootValueDeserializer(DeserializationContext.java:669)
	at com.fasterxml.jackson.databind.ObjectReader._prefetchRootDeserializer(ObjectReader.java:2456)
	at com.fasterxml.jackson.databind.ObjectReader.<init>(ObjectReader.java:194)
	at com.fasterxml.jackson.databind.ObjectMapper._newReader(ObjectMapper.java:809)
	at com.fasterxml.jackson.databind.ObjectMapper.readerForUpdating(ObjectMapper.java:4352)
	at ru.citeck.ecos.commons.json.JsonMapperImpl.applyData(JsonMapperImpl.kt:129)
	at ru.citeck.ecos.apps.domain.TestAbc.test(TestAbc.kt:23)
	at java.base/java.lang.reflect.Method.invoke(Method.java:565)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1604)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1604)
 */
}
