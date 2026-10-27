package com.salmanmalvasi.studentcc.models;

import java.util.Objects;

public class EventClub {
    public String id;
    public String name;

    public EventClub(String id, String name) {
        this.id = id;
        this.name = name;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventClub eventClub = (EventClub) o;
        return Objects.equals(id, eventClub.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name;
    }
}
