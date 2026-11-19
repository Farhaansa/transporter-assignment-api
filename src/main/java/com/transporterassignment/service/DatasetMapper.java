package com.transporterassignment.service;

import com.transporterassignment.dto.InputRequest;
import com.transporterassignment.dto.LaneDto;
import com.transporterassignment.dto.LaneQuoteDto;
import com.transporterassignment.dto.TransporterDto;
import com.transporterassignment.model.Dataset;
import com.transporterassignment.model.Lane;
import com.transporterassignment.model.Transporter;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/** Converts a validated request DTO into the immutable domain model. */
@Component
public class DatasetMapper {

    public Dataset toDataset(InputRequest request) {
        return new Dataset(
                request.lanes().stream().map(DatasetMapper::toLane).toList(),
                request.transporters().stream().map(DatasetMapper::toTransporter).toList());
    }

    private static Lane toLane(LaneDto lane) {
        return new Lane(lane.id(), lane.origin(), lane.destination());
    }

    private static Transporter toTransporter(TransporterDto transporter) {
        return new Transporter(transporter.id(), transporter.name(), transporter.laneQuotes().stream()
                .collect(Collectors.toMap(LaneQuoteDto::laneId, LaneQuoteDto::quote)));
    }
}
