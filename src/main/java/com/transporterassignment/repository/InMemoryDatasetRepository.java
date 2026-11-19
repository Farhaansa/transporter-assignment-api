package com.transporterassignment.repository;

import com.transporterassignment.model.Dataset;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps the latest input in memory. {@link Dataset} is immutable, so swapping one reference is enough
 * for readers to always see either the old input or the new one, never a mix.
 */
@Repository
public class InMemoryDatasetRepository implements DatasetRepository {

    private final AtomicReference<Dataset> current = new AtomicReference<>();

    @Override
    public void save(Dataset dataset) {
        current.set(Objects.requireNonNull(dataset, "dataset"));
    }

    @Override
    public Optional<Dataset> find() {
        return Optional.ofNullable(current.get());
    }

    @Override
    public void clear() {
        current.set(null);
    }
}
