package com.example.ilgeobolkka.airoute.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class AiRouteCurrentId implements Serializable {

    private Long readerId;
    private Long bookId;
}
