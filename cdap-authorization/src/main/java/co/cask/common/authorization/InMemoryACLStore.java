/*
 * Copyright © 2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package co.cask.common.authorization;

import com.google.common.collect.Sets;

import java.util.Iterator;
import java.util.Set;

/**
 * In-memory implementation of {@link ACLStore}.
 */
public class InMemoryACLStore implements ACLStore {

  private Set<ACLEntry> store = Sets.newHashSet();

  @Override
  public void write(ACLEntry entry) {
    store.add(entry);
  }

  @Override
  public void exists(ACLEntry entry) {
    store.contains(entry);
  }

  @Override
  public void delete(ACLEntry entry) {
    store.remove(entry);
  }

  @Override
  public Set<ACLEntry> read(Query query) {
    Set<ACLEntry> result = Sets.newHashSet();
    for (ACLEntry aclEntry : store) {
      for (Condition condition : query.getConditions()) {
        if (condition.matches(aclEntry)) {
          result.add(aclEntry);
          break;
        }
      }
    }

    return result;
  }

  @Override
  public void delete(Query query) {
    Iterator<ACLEntry> iterator = store.iterator();
    while (iterator.hasNext()) {
      ACLEntry aclEntry = iterator.next();
      for (Condition condition : query.getConditions()) {
        if (condition.matches(aclEntry)) {
          iterator.remove();
          break;
        }
      }
    }
  }
}
