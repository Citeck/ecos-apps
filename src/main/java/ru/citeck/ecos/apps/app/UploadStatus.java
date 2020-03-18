package ru.citeck.ecos.apps.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadStatus<E, R> {
    private E entity;
    private R entityRev;
    private boolean changed;
}
