/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.managed

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class ManagedScalarCollectionsIntegrationTest extends AbstractIntegrationSpec {

    private final static List<String> MANAGED_SCALAR_COLLECTION_TYPES = ['List', 'Set']

    @Unroll
    def "rule can mutate a managed type with a #type of scalar read-only property"() {
        given:
        buildScript """

        @Managed
        interface Container {
            $type<String> getItems()
        }

        class Rules extends RuleSource {
            @Model
            void createContainer(Container c) {}

            @Mutate
            void addItems(Container c) {
                c.items.add 'foo'
            }

            @Mutate
            void addCheckTask(ModelMap<Task> tasks, Container c) {
                tasks.create('check') {
                    assert c.items == ['foo'] as $type
                }
            }
        }

        apply plugin: Rules
        """

        expect:
        succeeds 'check'

        where:
        type << MANAGED_SCALAR_COLLECTION_TYPES
    }

    @Unroll
    def "rule can mutate a managed type with a #type of scalar read-write property"() {
        given:
        buildScript """

        @Managed
        interface Container {
            $type<String> getItems()
            void setItems($type<String> items)
        }

        class Rules extends RuleSource {
            static final $type<String> INITIAL = ['initial']

            @Model
            void createContainer(Container c) {
                assert c.items == null
                c.items = INITIAL
            }

            @Mutate
            void addItems(Container c) {
                assert !c.items.is(INITIAL)
                c.items.add 'foo'
            }

            @Mutate
            void addCheckTask(ModelMap<Task> tasks, Container c) {
                tasks.create('check') {
                    assert c.items == ['initial','foo'] as $type
                }
            }
        }

        apply plugin: Rules
        """

        expect:
        succeeds 'check'

        where:
        type << MANAGED_SCALAR_COLLECTION_TYPES
    }

    @Unroll
    def "rule can nullify a managed type with a #type of scalar read-write property"() {
        given:
        buildScript """

        @Managed
        interface Container {
            $type<String> getItems()
            void setItems($type<String> items)
        }

        class Rules extends RuleSource {
            static final $type<String> INITIAL = ['initial']

            @Model
            void createContainer(Container c) {
                assert c.items == null
                c.items = INITIAL
            }

            @Mutate
            void nullify(Container c) {
                c.items = null
            }

            @Mutate
            void addCheckTask(ModelMap<Task> tasks, Container c) {
                tasks.create('check') {
                    assert c.items == null
                }
            }
        }

        apply plugin: Rules
        """

        expect:
        succeeds 'check'

        where:
        type << MANAGED_SCALAR_COLLECTION_TYPES
    }

    @Unroll
    def "rule can overwrite value of a managed type with a #type of scalar read-write property"() {
        given:
        buildScript """

        @Managed
        interface Container {
            $type<String> getItems()
            void setItems($type<String> items)
        }

        class Rules extends RuleSource {
            static final $type<String> INITIAL = ['initial']
            static final $type<String> REPLACEMENT = ['b', 'c']

            @Model
            void createContainer(Container c) {
                assert c.items == null
                c.items = INITIAL
            }

            @Mutate
            void overwrite(Container c) {
                c.items = ['b','c']
            }

            @Mutate
            void addCheckTask(ModelMap<Task> tasks, Container c) {
                tasks.create('check') {
                    assert !c.items.is(REPLACEMENT)
                    assert c.items == ['b','c'] as $type
                }
            }
        }

        apply plugin: Rules
        """

        expect:
        succeeds 'check'

        where:
        type << MANAGED_SCALAR_COLLECTION_TYPES
    }

    @Unroll
    def "rule can nullify and set value of a managed type #type in the same mutation block"() {
        given:
        buildScript """

        @Managed
        interface Container {
            $type<String> getItems()
            void setItems($type<String> items)
        }

        class Rules extends RuleSource {
            static final $type<String> INITIAL = ['initial']

            @Model
            void createContainer(Container c) {
                assert c.items == null
                c.items = INITIAL
            }

            @Mutate
            void replace(Container c) {
                c.items = null
                c.items = ['b','c']
            }

            @Mutate
            void addCheckTask(ModelMap<Task> tasks, Container c) {
                tasks.create('check') {
                    assert c.items == ['b','c'] as $type
                }
            }
        }

        apply plugin: Rules
        """

        expect:
        succeeds 'check'

        where:
        type << MANAGED_SCALAR_COLLECTION_TYPES
    }

    @Unroll
    def "rule cannot mutate a managed type with a #type of scalar property when not the subject of the rule"() {
        when:
        buildScript """

        @Managed
        interface Container {
            $type<String> getItems()
        }

        class Rules extends RuleSource {
            @Model
            void createContainer(Container c) {}

            @Mutate
            void addItems(Container c) {
                c.items.add 'foo'
            }

            @Mutate
            void tryToMutate(ModelMap<Task> map, Container c) {
                c.items.add 'foo'
            }
        }

        apply plugin: Rules
        """

        then:
        fails 'tasks'

        and:
        failure.assertHasCause "Attempt to mutate closed view of model of type 'java.util.$type<java.lang.String>' given to rule 'Rules#tryToMutate'"

        where:
        type << MANAGED_SCALAR_COLLECTION_TYPES
    }

    @Unroll
    def "rule cannot mutate closed view even using iterator on #type"() {
        when:
        buildScript """

        @Managed
        interface Container {
            $type<String> getItems()
        }

        class Rules extends RuleSource {
            @Model
            void createContainer(Container c) {}

            @Mutate
            void addItems(Container c) {
                c.items.add 'foo'
            }

            @Mutate
            void tryToMutate(ModelMap<Task> map, Container c) {
                def it = c.items.iterator()
                it.next()
                it.remove()
            }
        }

        apply plugin: Rules
        """

        then:
        fails 'tasks'

        and:
        failure.assertHasCause "Attempt to mutate closed view of model of type 'java.util.$type<java.lang.String>' given to rule 'Rules#tryToMutate'"

        where:
        type << MANAGED_SCALAR_COLLECTION_TYPES

    }

    @Unroll
    def "reports problem when managed type declares a #type of managed type"() {
        when:
        buildScript """
        @Managed
        interface Thing { }

        @Managed
        interface Container {
            $type<Thing> getItems()
        }

        model {
            container(Container)
        }
        """

        then:
        fails 'model'

        and:
        failure.assertHasCause "Exception thrown while executing model rule: container(Container) @ build.gradle line 11, column 13"
        failure.assertHasCause("""A model element of type: 'Container' can not be constructed.
Its property 'java.util.$type<Thing> items' is not a valid scalar collection
A scalar collection can not contain 'Thing's
A valid scalar collection takes the form of List<T> or Set<T> where 'T' is one of (String, Boolean, Character, Byte, Short, Integer, Float, Long, Double, BigInteger, BigDecimal, File)""")

        where:
        type << MANAGED_SCALAR_COLLECTION_TYPES
    }

    @Unroll
    def "reports problem when managed type declares a #type of subtype of scalar type"() {
        when:
        buildScript """
        class Thing extends File {
            Thing(String s) { super(s) }
        }

        @Managed
        interface Container {
            $type<Thing> getItems()
        }

        model {
            container(Container)
        }
        """

        then:
        fails 'model'

        and:
        failure.assertHasCause "Exception thrown while executing model rule: container(Container) @ build.gradle line 12, column 13"
        failure.assertHasCause("""A model element of type: 'Container' can not be constructed.
Its property 'java.util.$type<Thing> items' is not a valid scalar collection
A scalar collection can not contain 'Thing's
A valid scalar collection takes the form of List<T> or Set<T> where 'T' is one of (String, Boolean, Character, Byte, Short, Integer, Float, Long, Double, BigInteger, BigDecimal, File)""")

        where:
        type << MANAGED_SCALAR_COLLECTION_TYPES
    }
}
