package ru.citeck.ecos.apps.app.content;

import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.domain.EcosContentEntity;
import ru.citeck.ecos.apps.repository.EcosContentRepo;
import ru.citeck.ecos.apps.utils.digest.EappDigest;
import ru.citeck.ecos.apps.utils.digest.EappDigestUtils;

import java.io.*;
import java.nio.file.Path;

@Component
public class EcosContentDao {

    private EcosContentRepo repo;

    public EcosContentDao(EcosContentRepo repo) {
        this.repo = repo;
    }

    public EcosContentEntity upload(Path path) {
        return upload(path.toFile());
    }

    public EcosContentEntity upload(File file) {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            return upload(IOUtils.toByteArray(in));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public EcosContentEntity upload(byte[] data) {

        EappDigest digest = EappDigestUtils.getSha256Digest(new ByteArrayInputStream(data));
        EcosContentEntity content = repo.findContent(digest.getHash(), digest.getSize());

        if (content == null) {
            content = new EcosContentEntity();
            content.setData(data);
            content.setHash(digest.getHash());
            content.setSize(digest.getSize());
            content = repo.save(content);
        }

        return content;
    }

    public EcosContentEntity getContent(long id) {
        return repo.getOne(id);
    }
}
