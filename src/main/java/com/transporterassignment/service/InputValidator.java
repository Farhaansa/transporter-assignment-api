package com.transporterassignment.service;

import com.transporterassignment.dto.InputRequest;
import com.transporterassignment.dto.LaneDto;
import com.transporterassignment.dto.LaneQuoteDto;
import com.transporterassignment.dto.TransporterDto;
import com.transporterassignment.exception.InfeasibleAssignmentException;
import com.transporterassignment.exception.InvalidInputException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Cross-field rules that Bean Validation on the DTOs cannot express. Runs after the field-level checks,
 * so every field here is known to be present.
 */
@Component
public class InputValidator {

    /**
     * @throws InvalidInputException         400, listing every structural problem found
     * @throws InfeasibleAssignmentException 422, when some lane has no quote at all
     */
    public void validate(InputRequest request) {
        List<String> problems = new ArrayList<>();

        Set<Long> laneIds = new LinkedHashSet<>();
        for (LaneDto lane : request.lanes()) {
            if (!laneIds.add(lane.id())) {
                problems.add("Duplicate lane id " + lane.id());
            }
        }

        Set<Long> transporterIds = new HashSet<>();
        Set<Long> quotedLanes = new HashSet<>();
        for (TransporterDto transporter : request.transporters()) {
            if (!transporterIds.add(transporter.id())) {
                problems.add("Duplicate transporter id " + transporter.id());
            }
            Set<Long> quotedByThis = new HashSet<>();
            for (LaneQuoteDto quote : transporter.laneQuotes()) {
                if (!laneIds.contains(quote.laneId())) {
                    problems.add("Transporter " + transporter.id() + " quotes unknown lane id " + quote.laneId());
                } else if (!quotedByThis.add(quote.laneId())) {
                    problems.add("Transporter " + transporter.id() + " quotes lane " + quote.laneId() + " more than once");
                }
            }
            quotedLanes.addAll(quotedByThis);
        }

        if (!problems.isEmpty()) {
            throw new InvalidInputException("Input data is inconsistent.", problems);
        }

        Set<Long> unquoted = new TreeSet<>(laneIds);
        unquoted.removeAll(quotedLanes);
        if (!unquoted.isEmpty()) {
            throw new InfeasibleAssignmentException("No transporter quoted lane(s) " + unquoted
                    + ", so not every lane can be assigned. Add quotes for these lanes or remove them.");
        }
    }
}
