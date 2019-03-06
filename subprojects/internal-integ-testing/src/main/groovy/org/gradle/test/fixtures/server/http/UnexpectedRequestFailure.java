/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.test.fixtures.server.http;

public class UnexpectedRequestFailure extends AbstractFailure {
    private final String method;
    private final String path;

    public UnexpectedRequestFailure(String method, String path) {
        this(method, path, "");
    }

    public UnexpectedRequestFailure(String method, String path, String context) {
        super(new UnexpectedRequestException(String.format("Unexpected request %s %s received%s", method, withLeadingSlash(path), contextSuffix(context))));
        this.method = method;
        this.path = path;
    }

    @Override
    public ResponseProducer forOtherRequest(String requestMethod, String path, String context) {
        return new RequestConditionFailure(requestMethod, path, String.format("Failed to handle %s %s due to unexpected request %s %s received%s", requestMethod, withLeadingSlash(path), this.method, withLeadingSlash(this.path), contextSuffix(context)));
    }
}
