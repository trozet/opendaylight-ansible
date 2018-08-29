package org.opendaylight.ansible.southbound;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class AnsibleEvent {
    private String uuid;
    private ObjectNode event_data;
    private String stdout;
    private int counter;
    private int pid;
    private String created;
    private int end_line;
    private int start_line;
    private String event;

    public AnsibleEvent(String uuid, ObjectNode event_data, String stdout) {
        this.uuid = uuid;
        this.event_data = event_data;
        this.stdout = stdout;
    }

    public AnsibleEvent() {
    }

    public String getEvent_data() {
        return event_data.toString();
    }

    public String getUuid() {
        return uuid;
    }

    public String getStdout() {
        return stdout;
    }

    public void setEvent_data(ObjectNode event_data) {
        this.event_data = event_data;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public int getCounter() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    public int getEnd_line() {
        return end_line;
    }

    public void setEnd_line(int end_line) {
        this.end_line = end_line;
    }

    public int getPid() {
        return pid;
    }

    public void setPid(int pid) {
        this.pid = pid;
    }

    public int getStart_line() {
        return start_line;
    }

    public void setStart_line(int start_line) {
        this.start_line = start_line;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

}
