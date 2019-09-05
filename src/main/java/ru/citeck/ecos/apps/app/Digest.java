package ru.citeck.ecos.apps.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Digest {
    private String hash;
    private long size;
}
