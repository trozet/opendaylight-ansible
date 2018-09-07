/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.ansible.southbound;

import static org.opendaylight.ansible.mdsalutils.Datastore.OPERATIONAL;

import ch.vorburger.exec.ManagedProcess;
import ch.vorburger.exec.ManagedProcessBuilder;
import ch.vorburger.exec.ManagedProcessException;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.ansible.mdsalutils.RetryingManagedNewTransactionRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.AnsibleCommandService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.Commands;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.RunAnsibleCommandInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.RunAnsibleCommandOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.RunAnsibleCommandOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.Status;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.commands.Command;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.commands.CommandBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.commands.CommandKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.run.ansible.command.input.command.type.Playbook;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.run.ansible.command.input.command.type.Role;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.ops4j.pax.cdi.api.OsgiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AnsibleCommandServiceImpl implements AnsibleCommandService {
    private static final Logger LOG = LoggerFactory.getLogger(AnsibleCommandServiceImpl.class);
    private Map<Uuid, ManagedProcess> processMap = new ConcurrentHashMap<>();
    private final DataBroker dataBroker;
    private static final String ANSIBLEVAR = "ANSIBLEVAR";
    private static final String ROLEVAR = "ROLEVAR";
    private static final Map<String, String> VAR_MAP = new HashMap<String, String>() {
        {
            put(ANSIBLEVAR, "--cmdline");
            put(ROLEVAR, "--role-vars");
        }
    };

    @Inject
    public AnsibleCommandServiceImpl(@OsgiService final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public ListenableFuture<RpcResult<RunAnsibleCommandOutput>> runAnsibleCommand(RunAnsibleCommandInput input) {
        Uuid uuid;
        Status status;
        String failedEvent;
        try {
            if (input.getCommandType() instanceof Playbook) {
                uuid = runAnsiblePlaybook(input.getHost(), input.getDirectory(),
                        ((Playbook) input.getCommandType()).getFile(),
                        input.getAnsibleVars());
            } else {
                // Must be instance of Role
                uuid = runAnsibleRole(input.getHost(), input.getDirectory(),
                        ((Role) input.getCommandType()).getRoleName(),
                        ((Role) input.getCommandType()).getRoleVars(),
                        input.getAnsibleVars());
            }
            status = Status.InProgress;
            failedEvent = null;

        } catch (ManagedProcessException e) {
            status = Status.Failed;
            uuid = null;
            failedEvent = e.getMessage();
        }
        return RpcResultBuilder.success(new RunAnsibleCommandOutputBuilder().setStatus(status).setUuid(uuid)
                .setFailedEvent(failedEvent).build()).buildFuture();
    }

    private Uuid runAnsibleRole(String host, String dir, String role, List<String> roleVars, List<String> ansibleVars)
            throws ManagedProcessException {
        ManagedProcessBuilder ar = new ManagedProcessBuilder("ansible-runner").addArgument("-j")
                .addArgument("--hosts").addArgument(host).addArgument("-r").addArgument(role);
        ar = injectVars(ar, ROLEVAR, roleVars);
        ar = injectVars(ar, ANSIBLEVAR, ansibleVars);
        ar.addArgument("run").addArgument(dir);
        return runAnsible(ar);
    }

    private Uuid runAnsiblePlaybook(String host, String dir, String file, List<String> ansibleVars)
            throws ManagedProcessException {
        ManagedProcessBuilder ar = new ManagedProcessBuilder("ansible-runner").addArgument("-j")
                .addArgument("--hosts").addArgument(host).addArgument("-p").addArgument(file);
        ar = injectVars(ar, ANSIBLEVAR, ansibleVars);
        ar.addArgument("run").addArgument(dir);
        return runAnsible(ar);
    }

    private Uuid runAnsible(ManagedProcessBuilder builder) {
        Uuid uuid = new Uuid(UUID.randomUUID().toString());
        LOG.info("Executing Ansible, new uuid for command is: {}", uuid);
        builder.setProcessListener(new AnsibleProcessListener(this, uuid));
        ManagedProcess mp = builder.build();
        processMap.put(uuid, mp);
        LOG.info("Starting Ansible process");
        try {
            mp.start();
            LOG.info("Ansible Process is alive: {}", Boolean.toString(mp.isAlive()));
        } catch (ManagedProcessException e) {
            LOG.warn("Process exited with error code: {}", mp.getProcLongName());
        }
        return uuid;
    }

    private ManagedProcessBuilder injectVars(ManagedProcessBuilder builder, String varType, List<String> varList) {
        if (varList != null && ! varList.isEmpty()) {
            if (VAR_MAP.get(varType) == null) {
                LOG.warn("Unable to determine variable types to add to ansible command {}", varType);
            } else {
                builder.addArgument(VAR_MAP.get(varType));
                if (varType.equals(ANSIBLEVAR)) {
                    builder.addArgument("'-e" + " " + String.join(" ", varList) + "'", false);
                } else {
                    builder.addArgument(String.join(" ", varList));
                }
            }
        }
        return builder;
    }

    public void parseAnsibleResult(Uuid uuid) throws AnsibleCommandException {
        ManagedProcess mp = getProcess(uuid);
        if (mp == null) {
            throw new AnsibleCommandException("Unable to find process for uuid" + uuid.toString());
        }
        parseAnsibleResult(mp, uuid);
    }

    public void parseAnsibleResult(ManagedProcess mp, Uuid uuid) {
        String output = mp.getConsole();
        Status result;
        String failedEventOutput = null;
        LOG.info("Ansible process complete: {}", output);
        try {
            LOG.info("Parsing json string into Event List");
            AnsibleEventList el = new AnsibleEventList(parseAnsibleOutput(output));
            AnsibleEvent lastEvent = el.getLastEvent();
            LOG.info("Stdout of last event is {}", lastEvent.getStdout());
            if (el.ansiblePassed()) {
                LOG.info("Ansible Passed for {}", mp.getProcLongName());
                result = Status.Complete;
            } else {
                result = Status.Failed;
                LOG.error("Ansible Failed for " + mp.getProcLongName());
                AnsibleEvent failedEvent = el.getFailedEvent();
                if (failedEvent != null) {
                    LOG.error("Failed Event Output: " + failedEvent.getStdout());
                    failedEventOutput = failedEvent.getStdout();
                } else {
                    LOG.error("Unable to determine failed event");
                }
            }
        } catch (IOException | AnsibleCommandException e) {
            LOG.error("Unable to determine Ansible execution result {}", e.getMessage());
            result = Status.Failed;
        }

        updateAnsibleResult(result, failedEventOutput, uuid);
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
        jsonStringBuilder.deleteCharAt(jsonStringBuilder.length() - 1);
        jsonStringBuilder.append("]");
        LOG.info("munged json is {}", jsonStringBuilder.toString());
        return jsonStringBuilder.toString();
    }

    private void updateAnsibleResult(Status result, String failedEvent, Uuid uuid) {
        CommandKey cmdKey = new CommandKey(uuid);
        InstanceIdentifier<Command> cmdPath = InstanceIdentifier.create(Commands.class).child(Command.class, cmdKey);
        Command cmd = new CommandBuilder().setStatus(result).setFailedEvent(failedEvent).setUuid(uuid).build();
        RetryingManagedNewTransactionRunner txRunner = new RetryingManagedNewTransactionRunner(dataBroker, 3);
        txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx -> {
            tx.put(cmdPath, cmd);
        });
    }

    public ManagedProcess getProcess(Uuid uuid) {
        if (processMap.containsKey(uuid)) {
            return processMap.get(uuid);
        }
        return null;
    }
}
