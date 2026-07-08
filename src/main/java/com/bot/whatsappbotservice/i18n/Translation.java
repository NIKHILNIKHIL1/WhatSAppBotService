package com.bot.whatsappbotservice.i18n;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A single language's translated name/description for a {@code Product} or {@code Category}. */
@Getter
@Setter
@NoArgsConstructor
@Embeddable
public class Translation {

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    public Translation(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
