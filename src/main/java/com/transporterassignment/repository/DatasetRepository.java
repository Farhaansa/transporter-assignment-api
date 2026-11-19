package com.transporterassignment.repository;

import com.transporterassignment.model.Dataset;

import java.util.Optional;

/** Holds the current input. Saving replaces whatever was stored before. */
public interface DatasetRepository {

    void save(Dataset dataset);

    Optional<Dataset> find();

    void clear();
}
