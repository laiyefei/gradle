/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.changedetection.rules

import spock.lang.Specification

class CachingTaskStateChangesTest extends Specification {
    def delegate = Mock(Iterable.class)
    def change1 = Mock(TaskStateChange)
    def change2 = Mock(TaskStateChange)
    def change3 = Mock(TaskStateChange)

    def cachingChanges = new CachingTaskStateChanges(2, delegate)
    def collectingVisitor = new CollectingTaskStateChangeVisitor()

    def "caches all reported changes under cache size"() {
        when:
        cachingChanges.accept(new CollectingTaskStateChangeVisitor())

        then:
        1 * delegate.iterator() >> [change1, change2].iterator()
        0 * _

        when:
        cachingChanges.accept(collectingVisitor)
        def reported = collectingVisitor.getChanges()

        then:
        0 * _

        and:
        reported == [change1, change2]
    }

    def "does not cache once reported changes exceed cache size"() {
        when:
        cachingChanges.accept(new CollectingTaskStateChangeVisitor())

        then:
        1 * delegate.iterator() >> [change1, change2, change3].iterator()
        0 * _

        when:
        cachingChanges.accept(new CollectingTaskStateChangeVisitor())
        cachingChanges.accept(collectingVisitor)
        def reported = collectingVisitor.changes

        then:
        2 * delegate.iterator() >> [change1, change2, change3].iterator() >> [change3, change2, change1].iterator()
        0 * _

        and:
        reported == [change3, change2, change1]
    }
}
