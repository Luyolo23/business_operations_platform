package com.capaciti.ops.response;

import com.capaciti.ops.ticket.Category;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "response_templates")
public class ResponseTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "tone", nullable = false)
    private Tone tone;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public Tone getTone() { return tone; }
    public void setTone(Tone tone) { this.tone = tone; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

}
