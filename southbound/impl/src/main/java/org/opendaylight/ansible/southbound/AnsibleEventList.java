package org.opendaylight.ansible.southbound;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;


public class AnsibleEventList {
    private List<AnsibleEvent> eventList;
    private static final Logger LOG = LoggerFactory.getLogger(AnsibleEventList.class);

    public AnsibleEventList(String jsonString) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        eventList = mapper.readValue(jsonString, new TypeReference<List<AnsibleEvent>>(){});
    }

    public List<AnsibleEvent> getEventList() {
        return eventList;
    }

    public AnsibleEvent getLastEvent() {
        return eventList.get(eventList.size()-1);
    }

    public boolean AnsiblePassed() throws AnsibleCommandException {
       ObjectNode lastData = getLastEvent().getEvent_data();
       LOG.info("Last event is: " + lastData.toString());
       if (!lastData.has("failures")) {
           throw new AnsibleCommandException("Unable to parse final Ansible output for failure:" + lastData.toString());
       }
       JsonNode x = lastData.findValue("failures");

       if (x.size() == 0) {
           return true;
       } else {
           LOG.error("Failure data is: " + x.toString());
           return false;
       }
    }

    public AnsibleEvent getFailedEvent() {
        for (AnsibleEvent i: eventList) {
            if (i.getEvent().equals("runner_on_failed")) {
                return i;
            }
        }
        return null;
    }
}
