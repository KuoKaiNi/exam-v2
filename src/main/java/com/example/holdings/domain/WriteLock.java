package com.example.holdings.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 只有一列（id = 1），用來讓所有寫入依序進行。 */
@Entity
@Table(name = "write_lock")
public class WriteLock {

    @Id
    private Integer id;

    protected WriteLock() {
    }

    public Integer getId() {
        return id;
    }
}
