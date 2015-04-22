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

package co.cask.cdap.cli.commandset;

import co.cask.cdap.cli.Categorized;
import co.cask.cdap.cli.CommandCategory;
import co.cask.cdap.cli.command.adapter.CreateAdapterCommand;
import co.cask.cdap.cli.command.adapter.DeleteAdapterCommand;
import co.cask.cdap.cli.command.adapter.DescribeAdapterCommand;
import co.cask.cdap.cli.command.adapter.GetAdapterLogsCommand;
import co.cask.cdap.cli.command.adapter.GetAdapterRunsCommand;
import co.cask.cdap.cli.command.adapter.ListAdaptersCommand;
import co.cask.cdap.cli.command.adapter.StartAdapterCommand;
import co.cask.cdap.cli.command.adapter.StopAdapterCommand;
import co.cask.common.cli.Command;
import co.cask.common.cli.CommandSet;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Injector;

/**
 * Adapter commands.
 */
public class AdapterCommands extends CommandSet<Command> implements Categorized {

  @Inject
  public AdapterCommands(Injector injector) {
    super(
      ImmutableList.<Command>builder()
        .add(injector.getInstance(CreateAdapterCommand.class))
        .add(injector.getInstance(DeleteAdapterCommand.class))
        .add(injector.getInstance(DescribeAdapterCommand.class))
        .add(injector.getInstance(GetAdapterLogsCommand.class))
        .add(injector.getInstance(GetAdapterRunsCommand.class))
        .add(injector.getInstance(ListAdaptersCommand.class))
        .add(injector.getInstance(StartAdapterCommand.class))
        .add(injector.getInstance(StopAdapterCommand.class))
        .build());
  }

  @Override
  public String getCategory() {
    return CommandCategory.LIFECYCLE.getName();
  }
}
