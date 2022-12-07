package io.camunda.connector.slack;

import java.util.Objects;

public class Conversation {

    private final String id;
    private final String name;

    public Conversation(com.slack.api.model.Conversation conversation) {
        this.id = conversation.getId();
        this.name = conversation.getName();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Conversation that = (Conversation) o;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "Conversation{" + "id='" + id + '\'' + ", name='" + name + '\'' + '}';
    }
}
