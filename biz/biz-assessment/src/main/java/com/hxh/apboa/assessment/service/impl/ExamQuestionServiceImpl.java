package com.hxh.apboa.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hxh.apboa.assessment.mapper.ExamQuestionMapper;
import com.hxh.apboa.assessment.service.ExamQuestionService;
import com.hxh.apboa.common.entity.ExamQuestion;
import com.hxh.apboa.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExamQuestionServiceImpl implements ExamQuestionService {
    private final ExamQuestionMapper questionMapper;

    @Override
    public List<ExamQuestion> listByCampaign(Long campaignId) {
        return questionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getCampaignId, campaignId)
                .orderByAsc(ExamQuestion::getSortOrder)
                .orderByAsc(ExamQuestion::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamQuestion create(ExamQuestion q) {
        if (q.getCampaignId() == null) throw new BusinessException("考试批次不能为空");
        if (q.getTitle() == null || q.getTitle().isBlank()) throw new BusinessException("请填写题目");
        ExamQuestion entity = new ExamQuestion();
        entity.setCampaignId(q.getCampaignId());
        entity.setTitle(q.getTitle().trim());
        entity.setScore(q.getScore() != null ? q.getScore() : 0);
        entity.setSuggestedMinutes(q.getSuggestedMinutes());
        entity.setTaskScenario(q.getTaskScenario());
        entity.setTaskRequirement(q.getTaskRequirement());
        entity.setSubmissionDeliverable(q.getSubmissionDeliverable());
        entity.setSortOrder(q.getSortOrder() != null ? q.getSortOrder() : 0);
        questionMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamQuestion update(Long id, ExamQuestion q) {
        ExamQuestion entity = questionMapper.selectById(id);
        if (entity == null) throw new BusinessException("试题不存在");
        if (q.getTitle() != null) entity.setTitle(q.getTitle().trim());
        if (q.getScore() != null) entity.setScore(q.getScore());
        if (q.getSuggestedMinutes() != null) entity.setSuggestedMinutes(q.getSuggestedMinutes());
        if (q.getTaskScenario() != null) entity.setTaskScenario(q.getTaskScenario());
        if (q.getTaskRequirement() != null) entity.setTaskRequirement(q.getTaskRequirement());
        if (q.getSubmissionDeliverable() != null) entity.setSubmissionDeliverable(q.getSubmissionDeliverable());
        if (q.getSortOrder() != null) entity.setSortOrder(q.getSortOrder());
        questionMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        questionMapper.deleteById(id);
    }
}
