package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.LocationOptions;
import uy.edu.um.proytic2026.dto.LocationOptions.DepartmentOption;
import uy.edu.um.proytic2026.dto.LocationOptions.LocalityOption;
import uy.edu.um.proytic2026.entity.Locality;
import uy.edu.um.proytic2026.exception.InvalidLocationException;
import uy.edu.um.proytic2026.repository.DepartmentRepository;
import uy.edu.um.proytic2026.repository.LocalityRepository;

import java.util.Objects;

/** Departamentos y localidades para los formularios que piden una ubicación (CU-08 en adelante). */
@Service
@RequiredArgsConstructor
public class LocationService {

    private final DepartmentRepository departmentRepository;
    private final LocalityRepository localityRepository;

    /** Departamentos y localidades, ordenados por nombre. */
    @Transactional(readOnly = true)
    public LocationOptions options() {
        return new LocationOptions(
                departmentRepository.findAllByOrderByNameAsc().stream()
                        .map(d -> new DepartmentOption(d.getId(), d.getName()))
                        .toList(),
                localityRepository.findAllWithDepartment().stream()
                        .map(l -> new LocalityOption(l.getId(), l.getDepartment().getId(), l.getName()))
                        .toList());
    }

    /**
     * La localidad elegida, comprobando que sea del departamento elegido.
     *
     * @throws InvalidLocationException si no existe o es de otro departamento.
     */
    @Transactional(readOnly = true)
    public Locality locality(Integer departmentId, Integer localityId) {
        Locality locality = localityRepository.findById(localityId).orElseThrow(InvalidLocationException::new);
        if (!Objects.equals(locality.getDepartment().getId(), departmentId)) {
            throw new InvalidLocationException();
        }
        return locality;
    }
}
