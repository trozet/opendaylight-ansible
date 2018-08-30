/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.ansible.southbound;

import static org.opendaylight.ansible.northbound.api.AnsibleCommand.ANSIBLE_COMMAND_PATH;

import ch.vorburger.exec.ManagedProcess;
import ch.vorburger.exec.ManagedProcessBuilder;
import ch.vorburger.exec.ManagedProcessException;
import java.io.IOException;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.mdsal.listener.AbstractSyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.northbound.rev180821.commands.Command;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.ops4j.pax.cdi.api.OsgiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class AnsibleCommandListener extends AbstractSyncDataTreeChangeListener<Command> {
    private static final Logger LOG = LoggerFactory.getLogger(AnsibleCommandListener.class);

    @Inject
    public AnsibleCommandListener(@OsgiService final DataBroker dataBroker) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION, ANSIBLE_COMMAND_PATH);
        LOG.info("constructor");
    }

    @Override
    public void add(@Nonnull InstanceIdentifier<Command> instanceIdentifier, @Nonnull Command command) {
        LOG.info("add: id: {}\nnode: {}", instanceIdentifier, command);
        try {
            runAnsible(command.getHost(), command.getDirectory(), command.getFile());
        } catch (ManagedProcessException e) {
            LOG.error("Failed to execute ansible: {}", e.getMessage());
        }
    }

    @Override
    public void remove(@Nonnull InstanceIdentifier<Command> instanceIdentifier, @Nonnull Command command) {
        LOG.info("remove: id: {}\nnode: {}", instanceIdentifier, command);
    }

    @Override
    public void update(@Nonnull InstanceIdentifier<Command> instanceIdentifier,
                       @Nonnull Command oldCommand, @Nonnull Command newCommand) {
        LOG.info("update: id: {}\nold node: {}\nold node: {}", instanceIdentifier, oldCommand, newCommand);
    }

    private void runAnsible(String host, String dir, String file) throws ManagedProcessException {
        LOG.info("Executing Ansible");
        // just use localhost for now, should be real host in the future
        ManagedProcessBuilder ar = new ManagedProcessBuilder("ansible-runner")
                .addArgument("-j").addArgument("--hosts").addArgument(host).addArgument("-p").addArgument(file);
        ar.addArgument("run").addArgument(dir);

        ManagedProcess p = ar.build();
        LOG.info("Starting Ansible process");
        try {
            p.start();
            LOG.info("Ansible Process is alive: {}", Boolean.toString(p.isAlive()));
            p.waitForExitMaxMsOrDestroy(120000);
        } catch (ManagedProcessException e) {
            LOG.warn("Process exited with error code: " + p.getProcLongName());
        }
        String output = p.getConsole();
        LOG.info("Ansible process complete: {}", output);
        try {
            LOG.info("Parsing json string into Event List");
            AnsibleEventList el = new AnsibleEventList(parseAnsibleOutput(output));
            AnsibleEvent lastEvent = el.getLastEvent();
            LOG.info("Stdout of last event is {}", lastEvent.getStdout());
            if (el.AnsiblePassed()) {
                LOG.info("Ansible Passed for " + p.getProcLongName());
            } else {
                LOG.error("Ansible Failed for " + p.getProcLongName());
                AnsibleEvent failedEvent = el.getFailedEvent();
                if (failedEvent != null) {
                    LOG.error("Failed Event Output: " + failedEvent.getStdout());
                } else {
                    LOG.error("Unable to determine failed event");
                }
            }
        } catch (IOException | AnsibleCommandException e) {
            LOG.error("Unable to determine Ansible execution result {}", e.getMessage());
        }
    }

    private String parseAnsibleOutput(String data) throws AnsibleCommandException {
        LOG.info("Parsing result");
        if (data.length() == 0) {
            throw new AnsibleCommandException("Empty data in ansible output");
        }
        String[] lines = data.split("\\r?\\n");
        StringBuilder jsonStringBuilder = new StringBuilder();
        jsonStringBuilder.append("[");
        for (String l : lines) {
            jsonStringBuilder.append(l).append(",");
        }
        jsonStringBuilder.deleteCharAt(jsonStringBuilder.length()-1);
        jsonStringBuilder.append("]");
        LOG.info("munged json is {}", jsonStringBuilder.toString());
        return jsonStringBuilder.toString();
    }
}
