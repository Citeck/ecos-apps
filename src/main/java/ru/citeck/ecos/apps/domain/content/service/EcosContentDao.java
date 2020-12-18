package ru.citeck.ecos.apps.domain.content.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity;
import ru.citeck.ecos.apps.domain.content.repo.EcosContentRepo;
import ru.citeck.ecos.commons.utils.digest.Digest;
import ru.citeck.ecos.commons.utils.digest.DigestAlgorithm;
import ru.citeck.ecos.commons.utils.digest.DigestUtils;

import java.io.*;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class EcosContentDao {

    private final EcosContentRepo repo;

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

        Digest digest = DigestUtils.getDigest(new ByteArrayInputStream(data), DigestAlgorithm.SHA256);
        EcosContentEntity content = repo.findFirstByHashAndSize(digest.getHash(), digest.getSize());

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
