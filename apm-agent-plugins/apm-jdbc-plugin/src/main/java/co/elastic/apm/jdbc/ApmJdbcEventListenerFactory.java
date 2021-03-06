/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
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
 * #L%
 */
package co.elastic.apm.jdbc;

import com.p6spy.engine.event.JdbcEventListener;
import com.p6spy.engine.spy.JdbcEventListenerFactory;

public class ApmJdbcEventListenerFactory implements JdbcEventListenerFactory {

    private static final ApmJdbcEventListener EVENT_LISTENER = new ApmJdbcEventListener();

    @Override
    public JdbcEventListener createJdbcEventListener() {
        return EVENT_LISTENER;
    }
}
