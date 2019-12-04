package ru.citeck.ecos.apps.app;

import java.util.function.Supplier;

public enum PublishPolicy {

    PUBLISH_IF_CHANGED,
    PUBLISH_IF_NOT_PUBLISHED,
    PUBLISH,
    NONE;

    public boolean shouldPublish(boolean isChanged, Supplier<PublishStatus> statusSupplier) {

        boolean shouldPublish = false;
        switch (this) {

            case PUBLISH_IF_CHANGED:

                shouldPublish = isChanged;
                break;

            case PUBLISH_IF_NOT_PUBLISHED:

                if (isChanged) {
                    shouldPublish = true;
                } else {
                    PublishStatus status = statusSupplier.get();
                    if (PublishStatus.DEPS_WAITING.equals(status)) {
                        shouldPublish = false;
                    } else {
                        shouldPublish = !PublishStatus.PUBLISHED.equals(status);
                    }
                }
                break;

            case PUBLISH:

                shouldPublish = true;
                break;
        }

        return shouldPublish;
    }
}
