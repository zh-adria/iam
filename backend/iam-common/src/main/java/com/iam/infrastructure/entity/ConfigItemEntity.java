package com.iam.infrastructure.entity;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "system_config",
       uniqueConstraints = @UniqueConstraint(columnNames = "cfg_key"))
public class ConfigItemEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "cfg_key", nullable = false, unique = true, length = 128)
    private String key;
    @Column(name = "cfg_value", nullable = false, columnDefinition = "text")
    private String value;
    @Column(name = "cfg_type", nullable = false, length = 16)
    private String type;
    @Column(length = 255)
    private String description;
    @Column(nullable = false, updatable = false)
    private Instant updatedAt;

    public ConfigItemEntity() { this.updatedAt = Instant.now(); }
    public ConfigItemEntity(String key, String value, String type, String description) {
        this.key = key; this.value = value; this.type = type; this.description = description;
        this.updatedAt = Instant.now();
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
