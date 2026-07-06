package com.iam.infrastructure.repository;

import com.iam.infrastructure.entity.ScimGroupMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScimGroupMemberRepository extends JpaRepository<ScimGroupMemberEntity, Long> {
    List<ScimGroupMemberEntity> findByGroupId(Long groupId);
    List<ScimGroupMemberEntity> findByGroupIdAndMemberType(Long groupId, String memberType);
    void deleteByGroupId(Long groupId);
}
