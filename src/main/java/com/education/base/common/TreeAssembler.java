package com.education.base.common;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Gom danh sách phẳng có {@code parentId} thành cây đa cấp. Nút có cha không nằm trong danh sách
 * được đưa lên mức gốc để không bị mất.
 */
@UtilityClass
public class TreeAssembler {

    public <T> List<T> build(List<T> flat,
                             Function<T, Long> idOf,
                             Function<T, Long> parentIdOf,
                             Function<T, List<T>> childrenOf,
                             Comparator<T> order) {
        Map<Long, T> byId = new LinkedHashMap<>();
        for (T node : flat) {
            byId.put(idOf.apply(node), node);
        }
        List<T> roots = new ArrayList<>();
        for (T node : byId.values()) {
            Long parentId = parentIdOf.apply(node);
            T parent = parentId == null ? null : byId.get(parentId);
            if (parent == null || parent == node) {
                roots.add(node);
            } else {
                childrenOf.apply(parent).add(node);
            }
        }
        sort(roots, childrenOf, order);
        return roots;
    }

    private <T> void sort(List<T> nodes, Function<T, List<T>> childrenOf, Comparator<T> order) {
        nodes.sort(order);
        for (T node : nodes) {
            sort(childrenOf.apply(node), childrenOf, order);
        }
    }
}
