BEGIN;
SELECT _v.register_patch('021-who5', NULL, NULL);

INSERT INTO assessment_type (assessment_type_id,description)
VALUES ('WHO5', 'World Health Organization Well-Being Index');

-- WHO5 leads to PHQ9 unless short-circuited.
INSERT INTO assessment (
	assessment_id,
	assessment_type_id,
	base_question,
	next_assessment_id,
	created,
  minimum_eligibility_score,
  answers_may_contain_pii
)
SELECT
	'e671bca1-13ae-48e8-95ee-bf0af53210c2',
	'WHO5',
	'Over the last two weeks,',
	a.assessment_id,
	now(),
	0,
	false
FROM assessment a
WHERE a.assessment_type_id = 'PHQ9';

-- PHQ9 goes to GAD7 (existing behavior, nothing to change there).
-- GAD7 used to go to PCPTSD, now we terminate at the end of GAD7.
UPDATE assessment SET next_assessment_id = NULL
WHERE assessment_type_id = 'GAD7';

INSERT INTO institution_assessment (institution_assessment_id, institution_id, assessment_id)
VALUES (uuid_generate_v4(), 'COBALT', 'e671bca1-13ae-48e8-95ee-bf0af53210c2');

-- WHO5 Question 1: I have felt cheerful and in good spirits...
INSERT INTO question (question_id, assessment_id, question_type_id, question_text, display_order)
VALUES ('ede1ac30-f314-40ae-8bcf-f1c509fbf1d8', 'e671bca1-13ae-48e8-95ee-bf0af53210c2', 'QUAD', 'I have felt cheerful and in good spirits...', 1);

-- WHO5 Question 2: I have felt calm and relaxed...
INSERT INTO question (question_id, assessment_id, question_type_id, question_text, display_order)
VALUES ('28889ce1-06f0-42e1-a86d-205b1d29bada', 'e671bca1-13ae-48e8-95ee-bf0af53210c2', 'QUAD', 'I have felt calm and relaxed...', 2);

-- WHO5 Question 3: I have felt active and vigorous...
INSERT INTO question (question_id, assessment_id, question_type_id, question_text, display_order)
VALUES ('b393c108-4669-432c-bfa2-d050ba884d4a', 'e671bca1-13ae-48e8-95ee-bf0af53210c2', 'QUAD', 'I have felt active and vigorous...', 3);

-- WHO5 Question 4: I woke up feeling fresh and rested...
INSERT INTO question (question_id, assessment_id, question_type_id, question_text, display_order)
VALUES ('47b198c0-ce1a-4e80-8647-1042e2e10c75', 'e671bca1-13ae-48e8-95ee-bf0af53210c2', 'QUAD', 'I woke up feeling fresh and rested...', 4);

-- WHO5 Question 5: My daily life has been filled with things that interest me...
INSERT INTO question (question_id, assessment_id, question_type_id, question_text, display_order)
VALUES ('d7da6324-4ffe-4ad1-a199-5ce011050fd3', 'e671bca1-13ae-48e8-95ee-bf0af53210c2', 'QUAD', 'My daily life has been filled with things that interest me...', 5);

-- WHO5 Question 1 Answers: I have felt cheerful and in good spirits.

--  All of the time = 5
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), 'ede1ac30-f314-40ae-8bcf-f1c509fbf1d8', 'All of the time', 1, 5, '28889ce1-06f0-42e1-a86d-205b1d29bada');

--  Most of the time = 4
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), 'ede1ac30-f314-40ae-8bcf-f1c509fbf1d8', 'Most of the time', 2, 4, '28889ce1-06f0-42e1-a86d-205b1d29bada');

--  More than half of the time = 3
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), 'ede1ac30-f314-40ae-8bcf-f1c509fbf1d8', 'More than half of the time', 3, 3, '28889ce1-06f0-42e1-a86d-205b1d29bada');

-- Less than half of the time = 2
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), 'ede1ac30-f314-40ae-8bcf-f1c509fbf1d8', 'Less than half of the time', 4, 2, '28889ce1-06f0-42e1-a86d-205b1d29bada');

-- Some of the time = 1
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), 'ede1ac30-f314-40ae-8bcf-f1c509fbf1d8', 'Some of the time', 5, 1, '28889ce1-06f0-42e1-a86d-205b1d29bada');

-- At no time = 0
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), 'ede1ac30-f314-40ae-8bcf-f1c509fbf1d8', 'At no time', 6, 0, '28889ce1-06f0-42e1-a86d-205b1d29bada');

-- WHO5 Question 2 Answers: I have felt calm and relaxed...

--  All of the time = 5
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), '28889ce1-06f0-42e1-a86d-205b1d29bada', 'All of the time', 1, 5, 'b393c108-4669-432c-bfa2-d050ba884d4a');

--  Most of the time = 4
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), '28889ce1-06f0-42e1-a86d-205b1d29bada', 'Most of the time', 2, 4, 'b393c108-4669-432c-bfa2-d050ba884d4a');

--  More than half of the time = 3
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), '28889ce1-06f0-42e1-a86d-205b1d29bada', 'More than half of the time', 3, 3, 'b393c108-4669-432c-bfa2-d050ba884d4a');

-- Less than half of the time = 2
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), '28889ce1-06f0-42e1-a86d-205b1d29bada', 'Less than half of the time', 4, 2, 'b393c108-4669-432c-bfa2-d050ba884d4a');

-- Some of the time = 1
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), '28889ce1-06f0-42e1-a86d-205b1d29bada', 'Some of the time', 5, 1, 'b393c108-4669-432c-bfa2-d050ba884d4a');

-- At no time = 0
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), '28889ce1-06f0-42e1-a86d-205b1d29bada', 'At no time', 6, 0, 'b393c108-4669-432c-bfa2-d050ba884d4a');

-- WHO5 Question 3 Answers: I have felt active and vigorous...

--  All of the time = 5
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), 'b393c108-4669-432c-bfa2-d050ba884d4a', 'All of the time', 1, 5, '47b198c0-ce1a-4e80-8647-1042e2e10c75');

--  Most of the time = 4
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), 'b393c108-4669-432c-bfa2-d050ba884d4a', 'Most of the time', 2, 4, '47b198c0-ce1a-4e80-8647-1042e2e10c75');

--  More than half of the time = 3
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), 'b393c108-4669-432c-bfa2-d050ba884d4a', 'More than half of the time', 3, 3, '47b198c0-ce1a-4e80-8647-1042e2e10c75');

-- Less than half of the time = 2
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), 'b393c108-4669-432c-bfa2-d050ba884d4a', 'Less than half of the time', 4, 2, '47b198c0-ce1a-4e80-8647-1042e2e10c75');

-- Some of the time = 1
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), 'b393c108-4669-432c-bfa2-d050ba884d4a', 'Some of the time', 5, 1, '47b198c0-ce1a-4e80-8647-1042e2e10c75');

-- At no time = 0
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), 'b393c108-4669-432c-bfa2-d050ba884d4a', 'At no time', 6, 0, '47b198c0-ce1a-4e80-8647-1042e2e10c75');

-- WHO5 Question 4 Answers: I woke up feeling fresh and rested...

--  All of the time = 5
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), '47b198c0-ce1a-4e80-8647-1042e2e10c75', 'All of the time', 1, 5, 'd7da6324-4ffe-4ad1-a199-5ce011050fd3');

--  Most of the time = 4
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), '47b198c0-ce1a-4e80-8647-1042e2e10c75', 'Most of the time', 2, 4, 'd7da6324-4ffe-4ad1-a199-5ce011050fd3');

--  More than half of the time = 3
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), '47b198c0-ce1a-4e80-8647-1042e2e10c75', 'More than half of the time', 3, 3, 'd7da6324-4ffe-4ad1-a199-5ce011050fd3');

-- Less than half of the time = 2
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), '47b198c0-ce1a-4e80-8647-1042e2e10c75', 'Less than half of the time', 4, 2, 'd7da6324-4ffe-4ad1-a199-5ce011050fd3');

-- Some of the time = 1
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), '47b198c0-ce1a-4e80-8647-1042e2e10c75', 'Some of the time', 5, 1, 'd7da6324-4ffe-4ad1-a199-5ce011050fd3');

-- At no time = 0
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), '47b198c0-ce1a-4e80-8647-1042e2e10c75', 'At no time', 6, 0, 'd7da6324-4ffe-4ad1-a199-5ce011050fd3');

-- WHO5 Question 5 Answers: My daily life has been filled with things that interest me...

--  All of the time = 5
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), 'd7da6324-4ffe-4ad1-a199-5ce011050fd3', 'All of the time', 1, 5, NULL);

--  Most of the time = 4
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), 'd7da6324-4ffe-4ad1-a199-5ce011050fd3', 'Most of the time', 2, 4, NULL);

--  More than half of the time = 3
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), 'd7da6324-4ffe-4ad1-a199-5ce011050fd3', 'More than half of the time', 3, 3, NULL);

-- Less than half of the time = 2
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), 'd7da6324-4ffe-4ad1-a199-5ce011050fd3', 'Less than half of the time', 4, 2, NULL);

-- Some of the time = 1
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), 'd7da6324-4ffe-4ad1-a199-5ce011050fd3', 'Some of the time', 5, 1, NULL);

-- At no time = 0
INSERT INTO answer (answer_id, question_id, answer_text, display_order, answer_value, next_question_id)
VALUES (uuid_generate_v4(), 'd7da6324-4ffe-4ad1-a199-5ce011050fd3', 'At no time', 6, 0, NULL);

END;