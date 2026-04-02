package com.youngstersclub.app.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "snooker_tables")
public class SnookerTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "table_name", unique = true, nullable = false)
    private String tableName;

    @JsonIgnore
    @OneToMany(mappedBy = "snookerTable", fetch = FetchType.LAZY)
    private List<Frame> frames;

    public SnookerTable() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public List<Frame> getFrames() { return frames; }
    public void setFrames(List<Frame> frames) { this.frames = frames; }
}
