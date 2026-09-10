package com.hxh.apboa.assessment.service;

import com.hxh.apboa.common.entity.ExamQuestion;

import java.util.List;

public interface ExamQuestionService {
    List<ExamQuestion> listByCampaign(Long campaignId);
    ExamQuestion create(ExamQuestion question);
    ExamQuestion update(Long id, ExamQuestion question);
    void delete(Long id);
}
