package za.co.capitecbank.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import za.co.capitecbank.adapter.model.ClientProfileResponse;
import za.co.capitecbank.persistence.entity.ClientProfileEntity;

@Mapper(componentModel = "spring")
public interface ClientProfileMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "ppiChangeHistory", ignore = true)
    ClientProfileEntity toEntity(ClientProfileResponse response);
}
