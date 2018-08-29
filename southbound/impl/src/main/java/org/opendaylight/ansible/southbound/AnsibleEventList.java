package org.opendaylight.ansible.southbound;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendaylight.ansible.southbound.AnsibleEvent;

import java.io.IOException;
import java.util.List;


public class AnsibleEventList {
    private List<AnsibleEvent> eventList;

    public AnsibleEventList(String jsonString) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        this.eventList = mapper.readValue(jsonString, new TypeReference<List<AnsibleEvent>>(){});
    }

    public List<AnsibleEvent> getEventList() {
        return eventList;
    }

    public AnsibleEvent getLastEvent() {
        return eventList.get(eventList.size()-1);
    }

}
