package dto;

import japicmp.model.JApiClass;

import java.util.List;

public record ClassSearchResult(List<JApiClass> exactMatches,List<JApiClass>  suffixMatches) {
}
