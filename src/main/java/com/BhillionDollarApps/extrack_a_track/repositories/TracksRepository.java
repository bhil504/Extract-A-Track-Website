package com.BhillionDollarApps.extrack_a_track.repositories;

import com.BhillionDollarApps.extrack_a_track.models.Tracks;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TracksRepository extends JpaRepository<Tracks, Long> {
    List<Tracks> findByUserId(Long userId); // Assuming each track belongs to a user
}
