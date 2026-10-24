const API_BASE = "/api";

// ---- simple state ----
let state = {
  subjectId: null,
  subjectName: "",
  topicId: null,
  topicName: "",
  questions: [],
  lastResult: null,
  usingDemo: false
};

// ================= DEMO DATA =================
const DEMO = {
  subjects: [
    { subjectId: 1, subjectName: "Java" },
    { subjectId: 2, subjectName: "Database Management" },
    { subjectId: 3, subjectName: "Web Development" }
  ],
  topics: {
    1: [
      { topicId: 101, topicName: "OOP Concepts" },
      { topicId: 102, topicName: "Exception Handling" },
      { topicId: 103, topicName: "Collections Framework" }
    ],
    2: [
      { topicId: 201, topicName: "Normalization" },
      { topicId: 202, topicName: "SQL Joins" }
    ],
    3: [
      { topicId: 301, topicName: "HTML & CSS Basics" },
      { topicId: 302, topicName: "REST APIs" }
    ]
  },
  notes: {
    101: "Object-Oriented Programming (OOP) is built on four main pillars:\n\n1. Encapsulation — bundling data and the methods that work on it into a single unit (a class), and restricting direct access to some of an object's components.\n\n2. Inheritance — a class can inherit fields and methods from another class, promoting code reuse.\n\n3. Polymorphism — the same method name can behave differently depending on the object calling it (method overloading and overriding).\n\n4. Abstraction — hiding complex implementation details and showing only the necessary features of an object.",
    102: "Exception handling in Java uses try, catch, finally, and throw/throws.\n\nA try block contains code that might throw an exception. A catch block handles a specific exception type. finally always runs, whether or not an exception occurred — commonly used to close resources like files or database connections.",
    103: "The Java Collections Framework provides ready-made data structures like ArrayList, LinkedList, HashSet, and HashMap. It saves you from writing your own list/array-handling logic and gives consistent, well-tested performance."
  },
  questions: {
    101: [
      { questionId: 1001, questionText: "Which OOP pillar hides internal implementation details?", optionA: "Inheritance", optionB: "Abstraction", optionC: "Polymorphism", optionD: "Encapsulation" },
      { questionId: 1002, questionText: "Which keyword lets one class inherit from another in Java?", optionA: "implements", optionB: "extends", optionC: "inherits", optionD: "super" },
      { questionId: 1003, questionText: "Overloading and overriding are examples of which OOP concept?", optionA: "Polymorphism", optionB: "Encapsulation", optionC: "Abstraction", optionD: "Inheritance" }
    ],
    102: [
      { questionId: 1004, questionText: "Which block always executes, exception or not?", optionA: "try", optionB: "catch", optionC: "finally", optionD: "throw" }
    ]
  },
  history: [
    { attemptId: 1, score: 3, totalQuestions: 3, percentage: 100 },
    { attemptId: 2, score: 1, totalQuestions: 3, percentage: 33.3 }
  ]
};

// Render free tier can take a while to wake up, so keep timeouts generous.
const DEFAULT_TIMEOUT = 20000;

// ---- element shortcuts ----
const views = {
  subjects: document.getElementById("view-subjects"),
  topics: document.getElementById("view-topics"),
  topicDetail: document.getElementById("view-topic-detail"),
  quiz: document.getElementById("view-quiz"),
  result: document.getElementById("view-result"),
  history: document.getElementById("view-history"),
};
const crumbsEl = document.getElementById("crumbs");
const emptyState = document.getElementById("emptyState");
const adminToggle = document.getElementById("adminToggle");
const studentIdInput = document.getElementById("studentIdInput");

function showView(name) {
  Object.values(views).forEach(v => v.classList.add("hidden"));
  views[name].classList.remove("hidden");
  emptyState.classList.add("hidden");
}

function setCrumbs(parts) {
  crumbsEl.innerHTML = "";
  parts.forEach((p, i) => {
    const span = document.createElement("span");
    span.textContent = p.label;
    if (p.onClick) {
      span.classList.add("crumb-link");
      span.addEventListener("click", p.onClick);
    }
    crumbsEl.appendChild(span);
    if (i < parts.length - 1) {
      const sep = document.createElement("span");
      sep.className = "crumb-sep";
      sep.textContent = "/";
      crumbsEl.appendChild(sep);
    }
  });
}

function showEmpty(msg) {
  emptyState.textContent = msg;
  emptyState.classList.remove("hidden");
}

async function apiGet(path, timeoutMs = DEFAULT_TIMEOUT) {
  const res = await fetch(API_BASE + path, { signal: AbortSignal.timeout(timeoutMs) });
  if (!res.ok) throw new Error("GET " + path + " failed: " + res.status);
  return res.json();
}

async function apiPost(path, body, timeoutMs = DEFAULT_TIMEOUT) {
  const res = await fetch(API_BASE + path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(timeoutMs)
  });

  const text = await res.text();

  if (!res.ok) {
    throw new Error("POST " + path + " failed: " + res.status + " " + text);
  }

  // Some endpoints return an empty body or plain text — don't treat that as a failure.
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch (e) {
    return text;
  }
}

// Saving only works against the real backend/database, not the demo data.
function ensureNotDemo() {
  if (state.usingDemo) {
    alert(
      "The backend didn't respond, so the app is showing demo data. " +
      "Demo items don't exist in the database, so nothing can be saved. " +
      "Wait for the server to wake up, then refresh the page."
    );
    return false;
  }
  return true;
}

// ---- ADMIN MODE toggle ----
adminToggle.addEventListener("change", () => {
  document.querySelectorAll(".admin-form").forEach(f => {
    f.classList.toggle("hidden", !adminToggle.checked);
  });
});

// ================= SUBJECTS =================
async function loadSubjects() {
  showView("subjects");
  setCrumbs([{ label: "Subjects" }]);
  const list = document.getElementById("subjectList");
  list.innerHTML = "";
  let subjects;
  try {
    subjects = await apiGet("/subjects");
    state.usingDemo = false;
  } catch (e) {
    console.warn("Backend not reachable, using demo subjects:", e);
    state.usingDemo = true;
    subjects = DEMO.subjects;
  }
  subjects.forEach(s => {
    const card = document.createElement("div");
    card.className = "item-card";
    card.innerHTML = `<h3>${escapeHtml(s.subjectName)}</h3><p>Tap to view topics</p>`;
    card.addEventListener("click", () => openSubject(s));
    list.appendChild(card);
  });
}

document.getElementById("addSubjectForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  if (!ensureNotDemo()) return;
  const nameInput = document.getElementById("newSubjectName");
  try {
    await apiPost("/subjects", { subjectName: nameInput.value });
    nameInput.value = "";
    await loadSubjects();
  } catch (err) {
    console.error("Failed to save subject:", err);
    alert("Subject could not be saved. Please check the Render logs.\n\n" + err.message);
  }
});

function openSubject(subject) {
  state.subjectId = subject.subjectId;
  state.subjectName = subject.subjectName;
  loadTopics();
}

// ================= TOPICS =================
async function loadTopics() {
  showView("topics");
  document.getElementById("topicsSubjectName").textContent = state.subjectName + " — Topics";
  setCrumbs([
    { label: "Subjects", onClick: loadSubjects },
    { label: state.subjectName }
  ]);
  const list = document.getElementById("topicList");
  list.innerHTML = "";
  let topics;
  try {
    topics = await apiGet("/topics/" + state.subjectId);
  } catch (e) {
    console.warn("Could not load topics from backend, using demo topics:", e);
    state.usingDemo = true;
    topics = DEMO.topics[state.subjectId] || [];
  }
  if (topics.length === 0) {
    showEmpty("No topics yet for this subject.");
    return;
  }
  topics.forEach(t => {
    const card = document.createElement("div");
    card.className = "item-card";
    card.innerHTML = `<h3>${escapeHtml(t.topicName)}</h3><p>Tap to read notes</p>`;
    card.addEventListener("click", () => openTopic(t));
    list.appendChild(card);
  });
}

document.getElementById("addTopicForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  if (!ensureNotDemo()) return;
  const nameInput = document.getElementById("newTopicName");
  try {
    await apiPost("/topics/" + state.subjectId, { topicName: nameInput.value });
    nameInput.value = "";
    await loadTopics();
  } catch (err) {
    console.error("Failed to save topic:", err);
    alert("Topic could not be saved. Please check the Render logs.\n\n" + err.message);
  }
});

function openTopic(topic) {
  state.topicId = topic.topicId;
  state.topicName = topic.topicName;
  loadTopicDetail();
}

// ================= TOPIC DETAIL (notes) =================
async function loadTopicDetail() {
  showView("topicDetail");
  document.getElementById("topicDetailName").textContent = state.topicName;
  setCrumbs([
    { label: "Subjects", onClick: loadSubjects },
    { label: state.subjectName, onClick: loadTopics },
    { label: state.topicName }
  ]);
  const notesEl = document.getElementById("notesContent");
  notesEl.textContent = "Loading notes...";
  try {
    const notes = await apiGet("/notes/" + state.topicId);
    notesEl.textContent = notes.length
      ? notes.map(n => n.content).join("\n\n")
      : (DEMO.notes[state.topicId] || "No notes added yet for this topic.");
  } catch (e) {
    notesEl.textContent = DEMO.notes[state.topicId] || "No notes added yet for this topic.";
  }
}

// ---- ADD NOTES ----
document.getElementById("addNotesForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  if (!ensureNotDemo()) return;
  const contentInput = document.getElementById("newNotesContent");
  try {
    await apiPost("/notes/" + state.topicId, {
      content: contentInput.value
    });

    alert("Notes saved successfully!");
    contentInput.value = "";
    await loadTopicDetail();
  } catch (err) {
    console.error("Failed to save notes:", err);
    alert("Notes could not be saved. Please check the Render logs.\n\n" + err.message);
  }
});   // <-- this closing was missing

// ---- ADD QUESTION ----
document.getElementById("addQuestionForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  if (!ensureNotDemo()) return;
  const payload = {
    questionText: document.getElementById("qText").value,
    optionA: document.getElementById("qA").value,
    optionB: document.getElementById("qB").value,
    optionC: document.getElementById("qC").value,
    optionD: document.getElementById("qD").value,
    correctOption: document.getElementById("qCorrect").value
  };
  try {
    await apiPost("/questions/" + state.topicId, payload);
    e.target.reset();
    alert("Question added successfully!");
  } catch (err) {
    console.error("Failed to save question:", err);
    alert("Question could not be saved. Please check the Render logs.\n\n" + err.message);
  }
});   // <-- this closing was missing too

// ================= QUIZ =================
// Generated quizzes use the rule-based Java QuizGeneratorService.
// Do NOT call /questions/{topicId} here (that is for manually stored questions).

document.getElementById("takeQuizBtn").addEventListener("click", loadQuiz);


function getQuizCount() {
  const selectors = [
    "#quizCount", "#questionCount", "#numQuestions",
    "#numberOfQuestions", 'input[name="count"]', 'input[name="questionCount"]'
  ];
  for (const selector of selectors) {
    const el = document.querySelector(selector);
    if (el && Number(el.value) > 0) return Math.min(30, Number(el.value));
  }
  return 8;
}

function getDifficulty() {
  const selectors = [
    "#difficulty", "#quizDifficulty", 'select[name="difficulty"]', 'input[name="difficulty"]'
  ];
  for (const selector of selectors) {
    const el = document.querySelector(selector);
    if (el && el.value) return String(el.value).toUpperCase();
  }
  return "ANY";
}

async function getNotesForQuiz() {
  try {
    const notes = await apiGet("/notes/" + state.topicId);
    if (Array.isArray(notes) && notes.length) {
      return notes.map(n => n.content || "").filter(Boolean).join("\n\n");
    }
  } catch (e) {
    console.warn("Could not load notes from backend:", e);
  }
  return DEMO.notes[state.topicId] || "";
}

// ---- question type + answer handling ----

const KNOWN_TYPES = ["MCQ", "TRUE_FALSE", "FILL_BLANK", "SHORT_ANSWER"];

function normaliseType(type) {
  const t = String(type || "")
    .trim()
    .toUpperCase()
    .replace(/[-\s]+/g, "_");

  if (t === "MCQ" || t === "MULTIPLE_CHOICE" || t === "MULTIPLECHOICE") return "MCQ";
  if (t === "TF" || t === "TRUE_FALSE" || t === "TRUEFALSE" || t === "TRUE_OR_FALSE") return "TRUE_FALSE";
  if (t === "SHORT" || t === "SHORT_ANSWER" || t === "SHORTANSWER") return "SHORT_ANSWER";
  if (t === "FILL" || t === "FILL_BLANK" || t === "FILLBLANK" || t === "FILL_IN_THE_BLANK") return "FILL_BLANK";
  return "";
}

function typeFromElement(el) {
  if (!el) return null;

  const raw = [
    el.dataset?.type,
    el.dataset?.questionType,
    el.dataset?.value,
    el.value,
    el.id,
    el.getAttribute?.("data-value"),
    el.textContent
  ].filter(Boolean).join(" ").toLowerCase();

  if (raw.includes("mcq") || raw.includes("multiple choice") || raw.includes("multiple_choice")) {
    return "MCQ";
  }
  if (raw.includes("true_false") || raw.includes("true or false") || raw.includes("true/false") ||
      raw.includes("true false") || raw === "tf" || raw.includes("truefalse")) {
    return "TRUE_FALSE";
  }
  if (raw.includes("short_answer") || raw.includes("short answer") || raw.includes("shortanswer")) {
    return "SHORT_ANSWER";
  }
  if (raw.includes("fill_blank") || raw.includes("fill in the blank") ||
      raw.includes("fill-in-the-blank") || raw.includes("fillblank")) {
    return "FILL_BLANK";
  }
  return null;
}

/*
 * IMPORTANT:
 * Do not infer a question's type from its question text or number of options.
 * The backend is the source of truth. The selected UI type is sent to the
 * backend, and the backend's returned type is rendered exactly as returned.
 */
function getSelectedQuizTypes() {
  const selected = [];

  // Normal checkbox/radio controls.
  document.querySelectorAll(
    'input[name="quizType"], input[name="quizTypes"], input[name="questionType"], ' +
    'input[name="questionTypes"], input[data-question-type], input[data-type]'
  ).forEach(el => {
    if (el.checked) {
      const type = typeFromElement(el);
      if (type) selected.push(type);
    }
  });

  // Buttons/cards used as type selectors.
  document.querySelectorAll(
    '[data-question-type].selected, [data-question-type].active, ' +
    '[data-type].selected, [data-type].active, ' +
    'button[aria-pressed="true"], [role="button"][aria-pressed="true"]'
  ).forEach(el => {
    const type = typeFromElement(el);
    if (type) selected.push(type);
  });

  if (selected.length) return [...new Set(selected)];

  // Multi/select dropdowns.
  for (const select of document.querySelectorAll("select")) {
    const looksLikeTypeSelect = /type|question/i.test(
      `${select.id} ${select.name} ${select.className}`
    );
    if (!looksLikeTypeSelect) continue;

    const values = Array.from(select.selectedOptions)
      .map(typeFromElement)
      .filter(Boolean);

    if (values.length) return [...new Set(values)];
  }

  return [];
}

function prepareQuestion(q, index) {
  const type = normaliseType(q?.type);

  if (!KNOWN_TYPES.includes(type)) {
    throw new Error(
      `Backend returned an invalid question type: "${q?.type ?? ""}".`
    );
  }

  const options = Array.isArray(q.options) ? q.options.filter(v => v != null && String(v).trim() !== "") : [];
  let answer = q.answer ?? q.correctAnswer ?? "";

  // Convert A/B/C/D answer letters to the actual option text.
  if (type === "MCQ" && /^[A-D]$/i.test(String(answer).trim())) {
    const optionIndex = String(answer).trim().toUpperCase().charCodeAt(0) - 65;
    answer = options[optionIndex] ?? "";
  }

  // If the backend returns the answer text, make it match the actual option
  // text/capitalisation exactly.
  if (type === "MCQ" && answer) {
    const matchingOption = options.find(
      option => String(option).trim().toLowerCase() === String(answer).trim().toLowerCase()
    );
    if (matchingOption) answer = matchingOption;
  }

  if (type === "TRUE_FALSE") {
    const a = String(answer).trim().toLowerCase();
    if (a === "true") answer = "True";
    else if (a === "false") answer = "False";
  }

  return {
    ...q,
    _index: index,
    type,
    options,
    answer,
    question: q.question ?? q.questionText ?? "",
    sourceSentence: q.sourceSentence ?? ""
  };
}

function validateGeneratedQuestion(q) {
  if (!q.question || !String(q.question).trim()) {
    return "A generated question has no question text.";
  }

  if (!q.answer || !String(q.answer).trim()) {
    return `Question "${q.question}" has no correct answer.`;
  }

  if (q.type === "MCQ") {
    if (q.options.length !== 4) {
      return `MCQ must have exactly 4 options, but received ${q.options.length}.`;
    }

    const answerMatches = q.options.filter(
      option => normaliseAnswer(option) === normaliseAnswer(q.answer)
    );

    if (answerMatches.length !== 1) {
      return "MCQ correct answer is not exactly one of its four options.";
    }
  }

  if (q.type === "TRUE_FALSE") {
    if (!["True", "False"].includes(q.answer)) {
      return "True/False question has an invalid correct answer.";
    }
  }

  return null;
}

async function loadQuiz() {
  showView("quiz");
  setCrumbs([
    { label: "Subjects", onClick: loadSubjects },
    { label: state.subjectName, onClick: loadTopics },
    { label: state.topicName, onClick: loadTopicDetail },
    { label: "Quiz" }
  ]);

  const form = document.getElementById("quizForm");
  form.innerHTML = "<p>Generating your mixed quiz...</p>";

  const submitButton = document.getElementById("submitQuizBtn");
  if (submitButton) {
    submitButton.disabled = false;
    submitButton.textContent = "Submit Quiz";
  }

  const count = getQuizCount();
  const difficulty = getDifficulty();
  const notes = await getNotesForQuiz();

  if (!notes || notes.trim().split(/\s+/).length < 40) {
    form.innerHTML = "";
    showEmpty("Please add at least about 40 words of notes before generating a quiz.");
    return;
  }

  try {
    // The backend always generates a mixed quiz:
    // MCQ + True/False + Short Answer.
    // Do not send selected question types from the UI.
    const body = {
      notes,
      count,
      difficulty
    };

    const generated = await apiPost("/quiz/generate", body, 30000);

    if (!Array.isArray(generated)) {
      throw new Error("Quiz generator returned an invalid response.");
    }

    console.log("Mixed quiz generated:", generated);

    // The backend is the source of truth. Never infer/relabel the type.
    const prepared = generated.map(prepareQuestion);

    for (const q of prepared) {
      const problem = validateGeneratedQuestion(q);

      if (problem) {
        throw new Error(problem);
      }

      if (!["MCQ", "TRUE_FALSE", "SHORT_ANSWER"].includes(q.type)) {
        throw new Error(
          `Unsupported generated question type: ${q.type}`
        );
      }
    }

    state.questions = prepared
      .slice(0, count)
      .map((q, i) => ({ ...q, _index: i }));

    if (!state.questions.length) {
      throw new Error("No usable questions were generated.");
    }

    document.getElementById("quizProgress").textContent =
      state.questions.length +
      " question" +
      (state.questions.length === 1 ? "" : "s") +
      " — answer each question";

    renderGeneratedQuiz();

  } catch (e) {
    console.error("Quiz generation failed:", e);

    form.innerHTML = `
      <div class="quiz-error">
        <strong>Could not generate the quiz.</strong>
        <p>${escapeHtml(e.message || "Unknown error")}</p>
        <button type="button" id="retryQuizBtn">Try Again</button>
      </div>
    `;

    document
      .getElementById("retryQuizBtn")
      ?.addEventListener("click", loadQuiz);
  }
}

function renderGeneratedQuiz() {
  const form = document.getElementById("quizForm");
  form.innerHTML = "";

  state.questions.forEach((q, idx) => {
    const block = document.createElement("div");
    block.className = "quiz-question generated-question";
    block.dataset.index = idx;

    const typeLabel = {
      MCQ: "Multiple Choice",
      TRUE_FALSE: "True / False",
      SHORT_ANSWER: "Short Answer"
    }[q.type] || "Question";

    let answerArea = "";

    if (q.type === "MCQ") {
      answerArea = q.options.map((option, optionIndex) => `
        <label class="quiz-option">
          <input type="radio" name="generated-q-${idx}" value="${optionIndex}">
          <span class="option-letter">${String.fromCharCode(65 + optionIndex)}</span>
          <span>${escapeHtml(option)}</span>
        </label>
      `).join("");
    } else if (q.type === "TRUE_FALSE") {
      answerArea = ["True", "False"].map((option, optionIndex) => `
        <label class="quiz-option">
          <input type="radio" name="generated-q-${idx}" value="${optionIndex}">
          <span class="option-letter">${optionIndex === 0 ? "T" : "F"}</span>
          <span>${option}</span>
        </label>
      `).join("");
    } else if (q.type === "FILL_BLANK") {
      answerArea = `
        <input
          type="text"
          class="fill-answer"
          name="generated-q-${idx}"
          placeholder="Type your answer here"
          autocomplete="off"
        >
      `;
    } else {
      // SHORT_ANSWER, and a safe fallback so the answer area is never empty
      answerArea = `
        <textarea
          class="short-answer"
          name="generated-q-${idx}"
          rows="3"
          placeholder="Write your answer here..."
        ></textarea>
      `;
    }

    block.innerHTML = `
      <div class="generated-type">${escapeHtml(typeLabel)}</div>
      <p class="qtext">${escapeHtml(cleanQuestionText(q.question))}</p>
      <div class="generated-answer-area">${answerArea}</div>
      <div class="question-feedback hidden"></div>
    `;

    form.appendChild(block);
  });
}

function getUserAnswer(q, index) {
  if (q.type === "MCQ") {
    const selected = document.querySelector(`input[name="generated-q-${index}"]:checked`);
    if (!selected) return "";
    const optionIndex = Number(selected.value);
    return Number.isInteger(optionIndex) && q.options[optionIndex] !== undefined
      ? q.options[optionIndex]
      : "";
  }

  if (q.type === "TRUE_FALSE") {
    const selected = document.querySelector(`input[name="generated-q-${index}"]:checked`);
    if (!selected) return "";
    return Number(selected.value) === 0 ? "True" : "False";
  }

  const input = document.querySelector(`[name="generated-q-${index}"]`);
  return input ? input.value.trim() : "";
}

function normaliseAnswer(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function shortAnswerCorrect(userAnswer, expectedAnswer) {
  const user = normaliseAnswer(userAnswer);
  const expected = normaliseAnswer(expectedAnswer);

  if (!user || !expected) return false;
  if (user === expected || expected.includes(user)) return true;

  const stop = new Set([
    "the", "a", "an", "is", "are", "was", "were", "of", "to", "and",
    "in", "on", "for", "with", "that", "this", "it", "as", "by", "from"
  ]);
  const expectedWords = expected.split(/\s+/).filter(w => w.length > 2 && !stop.has(w));
  const userWords = new Set(user.split(/\s+/).filter(w => w.length > 2 && !stop.has(w)));

  if (!expectedWords.length || !userWords.size) return false;
  const matches = expectedWords.filter(w => userWords.has(w)).length;
  return matches / expectedWords.length >= 0.45;
}

function isAnswerCorrect(q, userAnswer) {
  if (!userAnswer) return false;

  if (q.type === "MCQ" || q.type === "TRUE_FALSE" || q.type === "FILL_BLANK") {
    return normaliseAnswer(userAnswer) === normaliseAnswer(q.answer);
  }

  if (q.type === "SHORT_ANSWER") {
    return shortAnswerCorrect(userAnswer, q.answer);
  }

  return false;
}

function revealAnswerForQuestion(block, q, correct) {
  const feedback = block.querySelector(".question-feedback");
  feedback.classList.remove("hidden");

  if (correct) {
    feedback.className = "question-feedback correct";
    feedback.innerHTML = "✓ Correct!";
  } else {
    feedback.className = "question-feedback wrong";
    feedback.innerHTML = `✗ Wrong. <strong>Correct answer:</strong> ${escapeHtml(q.answer)}`;
  }

  block.querySelectorAll(".quiz-option").forEach(label => {
    label.classList.remove("selected-correct", "selected-wrong", "correct-option");
  });

  if (q.type === "MCQ" || q.type === "TRUE_FALSE") {
    const selected = block.querySelector(`input[name="generated-q-${q._index}"]:checked`);
    const selectedLabel = selected?.closest("label");
    if (selectedLabel) {
      selectedLabel.classList.add(correct ? "selected-correct" : "selected-wrong");
    }

    block.querySelectorAll(".quiz-option").forEach(label => {
      const input = label.querySelector("input");
      const optionIndex = Number(input?.value);
      const optionText = q.type === "MCQ"
        ? q.options[optionIndex]
        : (optionIndex === 0 ? "True" : "False");
      if (optionText && normaliseAnswer(optionText) === normaliseAnswer(q.answer)) {
        label.classList.add("correct-option");
      }
    });
  }
}

document.getElementById("submitQuizBtn").addEventListener("click", () => {
  if (!state.questions.length) return;

  let score = 0;

  state.questions.forEach((q, index) => {
    const block = document.querySelector(`.generated-question[data-index="${index}"]`);
    if (!block) return;

    const userAnswer = getUserAnswer(q, index);
    const correct = isAnswerCorrect(q, userAnswer);
    if (correct) score++;

    revealAnswerForQuestion(block, q, correct);

    block.querySelectorAll("input, textarea").forEach(el => {
      el.disabled = true;
    });
  });

  const total = state.questions.length;
  const percentage = total ? (score / total) * 100 : 0;

  // Generated questions have no database IDs, so /quiz/submit is not called.
  state.lastResult = { score, totalQuestions: total, percentage };

  const submitButton = document.getElementById("submitQuizBtn");
  if (submitButton) {
    submitButton.disabled = true;
    submitButton.textContent = `Score: ${score}/${total}`;
  }

  const progress = document.getElementById("quizProgress");
  if (progress) {
    progress.textContent = `Quiz checked — ${score}/${total} correct (${Math.round(percentage)}%)`;
  }
});

// ================= RESULT =================
function showResult(result) {
  showView("result");
  setCrumbs([{ label: "Result" }]);
  document.getElementById("resultScore").textContent = result.score + " / " + result.totalQuestions;
  document.getElementById("resultPct").textContent = Math.round(result.percentage) + "%";
}

document.getElementById("backToTopicBtn").addEventListener("click", loadTopicDetail);
document.getElementById("resultViewHistoryBtn").addEventListener("click", loadHistory);

// ================= HISTORY =================
document.getElementById("viewHistoryBtn").addEventListener("click", loadHistory);
document.getElementById("historyBackBtn").addEventListener("click", loadTopicDetail);

async function loadHistory() {
  showView("history");
  setCrumbs([{ label: "History" }]);
  const list = document.getElementById("historyList");
  list.innerHTML = "Loading...";

  let attempts;
  try {
    const studentId = Number(studentIdInput.value) || 1;
    attempts = await apiGet("/quiz/history/" + studentId);
    if (!attempts.length) attempts = DEMO.history;
  } catch (e) {
    attempts = DEMO.history;
  }

  list.innerHTML = "";
  attempts.slice().reverse().forEach(a => {
    const row = document.createElement("div");
    row.className = "history-row";
    const pct = Math.round(a.percentage);
    const scoreClass = pct >= 50 ? "score-good" : "score-bad";
    row.innerHTML = `
      <span>Attempt #${a.attemptId} — ${a.score}/${a.totalQuestions}</span>
      <span class="${scoreClass}">${pct}%</span>
    `;
    list.appendChild(row);
  });
}

// ---- helper to avoid raw HTML injection from notes/questions text ----
function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str ?? "";
  return div.innerHTML;
}

// ---- boot ----
loadSubjects();