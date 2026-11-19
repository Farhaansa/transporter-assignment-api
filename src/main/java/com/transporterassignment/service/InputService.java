package com.transporterassignment.service;

import com.transporterassignment.dto.InputRequest;
import com.transporterassignment.dto.InputResponse;
import com.transporterassignment.repository.DatasetRepository;
import org.springframework.stereotype.Service;

/** Validates new input and replaces the stored dataset. Rejected input leaves the previous data untouched. */
@Service
public class InputService {

    private final InputValidator validator;
    private final DatasetMapper mapper;
    private final DatasetRepository repository;

    public InputService(InputValidator validator, DatasetMapper mapper, DatasetRepository repository) {
        this.validator = validator;
        this.mapper = mapper;
        this.repository = repository;
    }

    public InputResponse replaceInput(InputRequest request) {
        validator.validate(request);
        repository.save(mapper.toDataset(request));
        return InputResponse.saved();
    }
}
