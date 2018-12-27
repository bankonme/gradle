/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.properties

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.ConventionTask
import org.gradle.internal.metaobject.DynamicObjectAware
import org.gradle.internal.extensibility.HasConvention
import org.gradle.internal.extensibility.IConventionAware
import org.gradle.api.internal.tasks.PropertySpecFactory
import org.gradle.api.internal.tasks.properties.annotations.ClasspathPropertyAnnotationHandler
import org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandler
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.reflect.PropertyMetadata
import org.gradle.internal.scripts.ScriptOrigin
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import javax.inject.Inject
import java.lang.annotation.Annotation

class DefaultTypePropertyMetadataStoreTest extends Specification {

    private static final List<Class<? extends Annotation>> PROCESSED_PROPERTY_TYPE_ANNOTATIONS = [
        InputFile, InputFiles, InputDirectory, OutputFile, OutputDirectory, OutputFiles, OutputDirectories
    ]

    private static final List<Class<? extends Annotation>> UNPROCESSED_PROPERTY_TYPE_ANNOTATIONS = [
        Console, Internal, Inject
    ]

    @Shared GroovyClassLoader groovyClassLoader

    def setupSpec() {
        groovyClassLoader = new GroovyClassLoader(getClass().classLoader)
    }

    static  class TaskWithCustomAnnotation extends DefaultTask {
        @SearchPath FileCollection searchPath
    }

    class SearchPathAnnotationHandler implements PropertyAnnotationHandler {

        @Override
        Class<? extends Annotation> getAnnotationType() {
            SearchPath
        }

        @Override
        boolean shouldVisit(PropertyVisitor visitor) {
            return true
        }

        @Override
        void visitPropertyValue(PropertyValue propertyInfo, PropertyVisitor visitor, PropertySpecFactory specFactory, BeanPropertyContext context) {
        }
    }

    def "can use custom annotation processor"() {
        def annotationHandler = new SearchPathAnnotationHandler()
        def metadataStore = new DefaultTypePropertyMetadataStore([annotationHandler], new TestCrossBuildInMemoryCacheFactory())

        when:
        def typePropertyMetadata = metadataStore.getTypePropertyMetadata(TaskWithCustomAnnotation)
        def propertiesMetadata = typePropertyMetadata.propertiesMetadata.findAll { !isIgnored(it) }

        then:
        propertiesMetadata.size() == 1
        def propertyMetadata = propertiesMetadata.first()
        propertyMetadata.fieldName == 'searchPath'
        propertyMetadata.propertyType == SearchPath
        propertyMetadata.validationMessages.empty
        typePropertyMetadata.getAnnotationHandlerFor(propertyMetadata) == annotationHandler
    }

    @Unroll
    def "can override @#parentAnnotation.simpleName property type with @#childAnnotation.simpleName"() {
        def parentTask = groovyClassLoader.parseClass """
            class ParentTask extends org.gradle.api.DefaultTask {
                @$parentAnnotation.name Object getValue() { null }
            }
        """

        def childTask = groovyClassLoader.parseClass """
            class ChildTask extends ParentTask {
                @Override @$childAnnotation.name Object getValue() { null }
            }
        """

        def metadataStore = new DefaultTypePropertyMetadataStore([], new TestCrossBuildInMemoryCacheFactory())

        def parentMetadata = metadataStore.getTypePropertyMetadata(parentTask).propertiesMetadata.first()
        def childMetadata = metadataStore.getTypePropertyMetadata(childTask).propertiesMetadata.first()

        expect:
        isOfType(parentMetadata, parentAnnotation)
        isOfType(childMetadata, childAnnotation)
        parentMetadata.validationMessages.empty
        childMetadata.validationMessages.empty

        where:
        [parentAnnotation, childAnnotation] << [PROCESSED_PROPERTY_TYPE_ANNOTATIONS, PROCESSED_PROPERTY_TYPE_ANNOTATIONS].combinations()*.flatten()
    }

    @Unroll
    def "can override @#processedAnnotation.simpleName property type with @#unprocessedAnnotation.simpleName"() {
        def parentTask = groovyClassLoader.parseClass """
            class ParentTask extends org.gradle.api.DefaultTask {
                @$processedAnnotation.name Object getValue() { null }
            }
        """

        def childTask = groovyClassLoader.parseClass """
            class ChildTask extends ParentTask {
                @Override @$unprocessedAnnotation.name Object getValue() { null }
            }
        """

        def metadataStore = new DefaultTypePropertyMetadataStore([], new TestCrossBuildInMemoryCacheFactory())

        def parentMetadata = metadataStore.getTypePropertyMetadata(parentTask).propertiesMetadata.first()
        def childMetadata = metadataStore.getTypePropertyMetadata(childTask).propertiesMetadata.first()

        expect:
        isOfType(parentMetadata, processedAnnotation)
        isIgnored(childMetadata)
        parentMetadata.validationMessages.empty
        childMetadata.validationMessages.empty

        where:
        [processedAnnotation, unprocessedAnnotation] << [PROCESSED_PROPERTY_TYPE_ANNOTATIONS, UNPROCESSED_PROPERTY_TYPE_ANNOTATIONS].combinations()*.flatten()
    }

    @Unroll
    def "can override @#unprocessedAnnotation.simpleName property type with @#processedAnnotation.simpleName"() {
        def parentTask = groovyClassLoader.parseClass """
            class ParentTask extends org.gradle.api.DefaultTask {
                @$unprocessedAnnotation.name Object getValue() { null }
            }
        """

        def childTask = groovyClassLoader.parseClass """
            class ChildTask extends ParentTask {
                @Override @$processedAnnotation.name Object getValue() { null }
            }
        """

        def metadataStore = new DefaultTypePropertyMetadataStore([], new TestCrossBuildInMemoryCacheFactory())

        def parentMetadata = metadataStore.getTypePropertyMetadata(parentTask).propertiesMetadata.first()
        def childMetadata = metadataStore.getTypePropertyMetadata(childTask).propertiesMetadata.first()

        expect:
        isIgnored(parentMetadata)
        isOfType(childMetadata, processedAnnotation)
        parentMetadata.validationMessages.empty
        childMetadata.validationMessages.empty

        where:
        [processedAnnotation, unprocessedAnnotation] << [PROCESSED_PROPERTY_TYPE_ANNOTATIONS, UNPROCESSED_PROPERTY_TYPE_ANNOTATIONS].combinations()*.flatten()
    }

    class ClasspathPropertyTask extends DefaultTask {
        @Classpath @InputFiles FileCollection inputFiles1
        @InputFiles @Classpath FileCollection inputFiles2
    }

    // Third-party plugins that need to support Gradle versions both pre- and post-3.2
    // need to declare their @Classpath properties as @InputFiles as well
    @Issue("https://github.com/gradle/gradle/issues/913")
    def "@Classpath takes precedence over @InputFiles when both are declared on property"() {
        def metadataStore = new DefaultTypePropertyMetadataStore([new ClasspathPropertyAnnotationHandler()], new TestCrossBuildInMemoryCacheFactory())

        when:
        def propertiesMetadata = metadataStore.getTypePropertyMetadata(ClasspathPropertyTask).propertiesMetadata.findAll { !isIgnored(it) }

        then:
        propertiesMetadata*.fieldName as List == ["inputFiles1", "inputFiles2"]
        propertiesMetadata*.propertyType as List == [Classpath, Classpath]
        propertiesMetadata*.validationMessages.flatten().empty
    }

    @Unroll
    def "all properties on #workClass are ignored"() {
        def metadataStore = new DefaultTypePropertyMetadataStore([], new TestCrossBuildInMemoryCacheFactory())

        when:
        def propertiesMetadata = metadataStore.getTypePropertyMetadata(workClass).propertiesMetadata.findAll { it.propertyType == null }
        then:
        propertiesMetadata*.fieldName.empty

        where:
        workClass << [ConventionTask.class, DefaultTask.class, AbstractTask.class, Task.class, Object.class, GroovyObject.class, IConventionAware.class, ExtensionAware.class, HasConvention.class, ScriptOrigin.class, DynamicObjectAware.class]
    }

    @SuppressWarnings("GrDeprecatedAPIUsage")
    static class SimpleTask extends DefaultTask {
        @Input String inputString
        @InputFile File inputFile
        @InputDirectory File inputDirectory
        @InputFiles File inputFiles
        @OutputFile File outputFile
        @OutputFiles Set<File> outputFiles
        @OutputDirectory File outputDirectory
        @OutputDirectories Set<File> outputDirectories
        @Inject Object injectedService
        @Internal Object internal
        @Console boolean console
    }

    def "can get annotated properties of simple task"() {
        def metadataStore = new DefaultTypePropertyMetadataStore([], new TestCrossBuildInMemoryCacheFactory())

        when:
        def propertiesMetadata = metadataStore.getTypePropertyMetadata(SimpleTask).propertiesMetadata

        then:
        nonIgnoredProperties(propertiesMetadata) == ["inputDirectory", "inputFile", "inputFiles", "inputString", "outputDirectories", "outputDirectory", "outputFile", "outputFiles"]
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    private static class IsGetterTask extends DefaultTask {
        @Input
        private boolean feature1
        private boolean feature2

        boolean isFeature1() {
            return feature1
        }
        void setFeature1(boolean enabled) {
            this.feature1 = enabled
        }
        boolean isFeature2() {
            return feature2
        }
        void setFeature2(boolean enabled) {
            this.feature2 = enabled
        }
    }

    private static boolean isOfType(PropertyMetadata metadata, Class<? extends Annotation> type) {
        metadata.propertyType == type
    }

    private static boolean isIgnored(PropertyMetadata propertyMetadata) {
        def propertyType = propertyMetadata.propertyType
        propertyType == null || UNPROCESSED_PROPERTY_TYPE_ANNOTATIONS.contains(propertyType)
    }

    private static List<String> nonIgnoredProperties(Collection<PropertyMetadata> typeMetadata) {
        typeMetadata.findAll { !isIgnored(it) }*.fieldName.sort()
    }
}