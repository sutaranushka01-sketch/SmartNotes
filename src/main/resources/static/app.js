
const API_BASE = "/api";

const state = {
  subjectId: null,
  subjectName: "",
  topicId: null,
  topicName: "",
  questions: [],
  lastResult: null,
  subjects: [],
  topics: [],
  demoMode: false,
  quizSubmitted: false
};

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
    101: "Object-Oriented Programming is based on four pillars: Encapsulation, Inheritance, Polymorphism and Abstraction. Encapsulation bundles data and methods inside a class. Inheritance allows a class to reuse fields and methods from another class. Polymorphism allows the same method name to behave differently. Abstraction hides implementation details and exposes essential functionality.",
    102: "Java exception handling uses try, catch, finally, throw and throws. A try block contains code that may cause an exception. A catch block handles an exception. The finally block is commonly used for cleanup and normally runs whether an exception occurs or not.",
    103: "The Java Collections Framework provides data structures such as ArrayList, LinkedList, HashSet and HashMap. These classes help developers store, organize and process collections of objects.",
    201: "Database normalization organizes relational tables to reduce redundant data and improve data integrity. Common normal forms include First Normal Form, Second Normal Form and Third Normal Form.",
    202: "SQL joins combine related rows from multiple tables. INNER JOIN returns matching rows. LEFT JOIN preserves every row from the left table. RIGHT JOIN preserves every row from the right table.",
    301: "HTML structures web pages using elements. CSS controls their appearance, layout, spacing, colors and responsive behavior.",
    302: "REST APIs expose resources through HTTP endpoints. Common methods include GET, POST, PUT, PATCH and DELETE. Responses often use JSON."
  },

  questions: {
    101: [
      {
        questionId: 1001,
        questionText: "Which OOP concept hides implementation details?",
        optionA: "Inheritance",
        optionB: "Abstraction",
        optionC: "Polymorphism",
        optionD: "Compilation",
        correctOption: "B"
      }
    ]
  },

  history: [
    { attemptId: 1, score: 3, totalQuestions: 3, percentage: 100 },
    { attemptId: 2, score: 1, totalQuestions: 3, percentage: 33.3 }
  ]
};

const views = {
  subjects: document.getElementById("view-subjects"),
  topics: document.getElementById("view-topics"),
  topicDetail: document.getElementById("view-topic-detail"),
  quiz: document.getElementById("view-quiz"),
  result: document.getElementById("view-result"),
  history: document.getElementById("view-history")
};

const crumbsEl = document.getElementById("crumbs");
const emptyState = document.getElementById("emptyState");
const adminToggle = document.getElementById("adminToggle");
const studentIdInput = document.getElementById("studentIdInput");

function $(selector) {
  return document.querySelector(selector);
}

function escapeHtml(value) {
  const div = document.createElement("div");
  div.textContent = value == null ? "" : String(value);
  return div.innerHTML;
}

function normaliseAnswer(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function notify(message, type = "info") {
  let host = document.getElementById("toastHost");

  if (!host) {
    host = document.createElement("div");
    host.id = "toastHost";
    host.className = "toast-host";
    host.setAttribute("aria-live", "polite");
    document.body.appendChild(host);
  }

  const toast = document.createElement("div");
  toast.className = `toast toast-${type}`;
  toast.textContent = message;
  host.appendChild(toast);

  requestAnimationFrame(() => toast.classList.add("toast-visible"));

  window.setTimeout(() => {
    toast.classList.remove("toast-visible");
    window.setTimeout(() => toast.remove(), 250);
  }, 3200);
}

function showView(name) {
  Object.entries(views).forEach(([key, view]) => {
    if (view) view.classList.toggle("hidden", key !== name);
  });

  if (emptyState) emptyState.classList.add("hidden");
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function setCrumbs(parts) {
  if (!crumbsEl) return;

  crumbsEl.replaceChildren();

  parts.forEach((part, index) => {
    const span = document.createElement("span");
    span.textContent = part.label;

    if (part.onClick) {
      span.className = "crumb-link";
      span.tabIndex = 0;
      span.setAttribute("role", "button");
      span.addEventListener("click", part.onClick);
      span.addEventListener("keydown", event => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          part.onClick();
        }
      });
    }

    crumbsEl.appendChild(span);

    if (index < parts.length - 1) {
      const separator = document.createElement("span");
      separator.className = "crumb-sep";
      separator.textContent = "/";
      crumbsEl.appendChild(separator);
    }
  });
}

function showEmpty(message) {
  if (!emptyState) return;

  emptyState.textContent = message;
  emptyState.classList.remove("hidden");
}

async function apiGet(path, timeoutMs = 8000) {
  const response = await fetch(API_BASE + path, {
    headers: { Accept: "application/json" },
    signal: AbortSignal.timeout(timeoutMs)
  });

  if (!response.ok) {
    throw new Error(`Request failed (${response.status})`);
  }

  return response.json();
}

async function apiPost(path, body, timeoutMs = 10000) {
  const response = await fetch(API_BASE + path, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json"
    },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(timeoutMs)
  });

  if (!response.ok) {
    const message = await response.text().catch(() => "");
    throw new Error(message || `Request failed (${response.status})`);
  }

  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

function setLoading(element, message = "Loading...") {
  if (element) {
    element.innerHTML = `<div class="loading-state"><span class="spinner"></span><span>${escapeHtml(message)}</span></div>`;
  }
}

// ADMIN MODE
if (adminToggle) {
  adminToggle.addEventListener("change", () => {
    document.querySelectorAll(".admin-form").forEach(form => {
      form.classList.toggle("hidden", !adminToggle.checked);
    });
  });
}

// SUBJECTS
async function loadSubjects() {
  showView("subjects");
  setCrumbs([{ label: "Subjects" }]);

  const list = document.getElementById("subjectList");
  if (!list) return;

  setLoading(list, "Loading your subjects...");

  try {
    state.subjects = await apiGet("/subjects");
    state.demoMode = false;
  } catch (error) {
    state.subjects = DEMO.subjects;
    state.demoMode = true;
  }

  list.replaceChildren();

  if (!state.subjects.length) {
    showEmpty("No subjects yet. Add a subject to get started.");
    return;
  }

  state.subjects.forEach(subject => {
    const card = document.createElement("button");
    card.type = "button";
    card.className = "item-card";
    card.innerHTML = `
      <span class="item-icon">📚</span>
      <h3>${escapeHtml(subject.subjectName)}</h3>
      <p>Explore topics and study notes</p>
      <span class="item-card-link">View topics →</span>
    `;
    card.addEventListener("click", () => openSubject(subject));
    list.appendChild(card);
  });
}

const addSubjectForm = document.getElementById("addSubjectForm");

if (addSubjectForm) {
  addSubjectForm.addEventListener("submit", async event => {
    event.preventDefault();

    const input = document.getElementById("newSubjectName");
    const subjectName = input?.value.trim();

    if (!subjectName) {
      notify("Enter a subject name first.", "error");
      return;
    }

    try {
      await apiPost("/subjects", { subjectName });
      input.value = "";
      notify("Subject added successfully.", "success");
      await loadSubjects();
    } catch (error) {
      notify(
        "Could not save the subject. Check your backend connection.",
        "error"
      );
    }
  });
}

function openSubject(subject) {
  state.subjectId = subject.subjectId;
  state.subjectName = subject.subjectName;
  loadTopics();
}

// TOPICS
async function loadTopics() {
  showView("topics");

  const heading = document.getElementById("topicsSubjectName");
  if (heading) heading.textContent = `${state.subjectName} — Topics`;

  setCrumbs([
    { label: "Subjects", onClick: loadSubjects },
    { label: state.subjectName }
  ]);

  const list = document.getElementById("topicList");
  if (!list) return;

  setLoading(list, "Loading topics...");

  try {
    state.topics = await apiGet("/topics/" + state.subjectId);
  } catch (error) {
    state.topics = DEMO.topics[state.subjectId] || [];
  }

  list.replaceChildren();

  if (!state.topics.length) {
    showEmpty("No topics yet. Add your first topic.");
    return;
  }

  state.topics.forEach(topic => {
    const card = document.createElement("button");
    card.type = "button";
    card.className = "item-card";
    card.innerHTML = `
      <span class="item-icon">📝</span>
      <h3>${escapeHtml(topic.topicName)}</h3>
      <p>Open notes and practice questions</p>
      <span class="item-card-link">Open topic →</span>
    `;
    card.addEventListener("click", () => openTopic(topic));
    list.appendChild(card);
  });
}

const addTopicForm = document.getElementById("addTopicForm");

if (addTopicForm) {
  addTopicForm.addEventListener("submit", async event => {
    event.preventDefault();

    if (!state.subjectId) {
      notify("Select a subject first.", "error");
      return;
    }

    const input = document.getElementById("newTopicName");
    const topicName = input?.value.trim();

    if (!topicName) {
      notify("Enter a topic name first.", "error");
      return;
    }

    try {
      await apiPost("/topics/" + state.subjectId, { topicName });
      input.value = "";
      notify("Topic added successfully.", "success");
      await loadTopics();
    } catch (error) {
      notify("Could not save the topic. Check your backend.", "error");
    }
  });
}

function openTopic(topic) {
  state.topicId = topic.topicId;
  state.topicName = topic.topicName;
  loadTopicDetail();
}

// NOTES
async function loadTopicDetail() {
  showView("topicDetail");

  const title = document.getElementById("topicDetailName");
  const notesElement = document.getElementById("notesContent");

  if (title) title.textContent = state.topicName;

  setCrumbs([
    { label: "Subjects", onClick: loadSubjects },
    { label: state.subjectName, onClick: loadTopics },
    { label: state.topicName }
  ]);

  if (!notesElement) return;

  notesElement.textContent = "Loading notes...";

  try {
    const notes = await apiGet("/notes/" + state.topicId);
    notesElement.textContent = Array.isArray(notes) && notes.length
      ? notes.map(note => note.content || "").filter(Boolean).join("\n\n")
      : DEMO.notes[state.topicId] || "No notes added yet.";
  } catch (error) {
    notesElement.textContent =
      DEMO.notes[state.topicId] || "No notes available. Check your backend connection.";
  }
}

const addNotesForm = document.getElementById("addNotesForm");

if (addNotesForm) {
  addNotesForm.addEventListener("submit", async event => {
    event.preventDefault();

    if (!state.topicId) {
      notify("Select a topic first.", "error");
      return;
    }

    const input = document.getElementById("newNotesContent");
    const content = input?.value.trim();

    if (!content) {
      notify("Write some notes before saving.", "error");
      return;
    }

    try {
      await apiPost("/notes/" + state.topicId, { content });
      input.value = "";
      notify("Notes saved successfully.", "success");
      await loadTopicDetail();
    } catch (error) {
      notify("Could not save notes. Check your backend.", "error");
    }
  });
}

// MANUAL QUESTION CREATION
const addQuestionForm = document.getElementById("addQuestionForm");

if (addQuestionForm) {
  addQuestionForm.addEventListener("submit", async event => {
    event.preventDefault();

    if (!state.topicId) {
      notify("Select a topic first.", "error");
      return;
    }

    const payload = {
      questionText: document.getElementById("qText")?.value.trim(),
      optionA: document.getElementById("qA")?.value.trim(),
      optionB: document.getElementById("qB")?.value.trim(),
      optionC: document.getElementById("qC")?.value.trim(),
      optionD: document.getElementById("qD")?.value.trim(),
      correctOption: document.getElementById("qCorrect")?.value
    };

    if (
      !payload.questionText ||
      !payload.optionA ||
      !payload.optionB ||
      !payload.optionC ||
      !payload.optionD ||
      !["A", "B", "C", "D"].includes(payload.correctOption)
    ) {
      notify("Complete the question and all four options.", "error");
      return;
    }

    try {
      await apiPost("/questions/" + state.topicId, payload);
      addQuestionForm.reset();
      notify("MCQ saved successfully.", "success");
    } catch (error) {
      notify("Could not save the question. Check your backend.", "error");
    }
  });
}

// QUIZ SETTINGS
function getQuizCount() {
  const selectors = [
    "#quizCount",
    "#questionCount",
    "#numQuestions",
    "#numberOfQuestions",
    'input[name="count"]',
    'input[name="questionCount"]'
  ];

  for (const selector of selectors) {
    const element = document.querySelector(selector);
    if (element && Number(element.value) > 0) {
      return Math.min(30, Math.floor(Number(element.value)));
    }
  }

  return 8;
}

function getDifficulty() {
  const selectors = [
    "#difficulty",
    "#quizDifficulty",
    'select[name="difficulty"]',
    'input[name="difficulty"]'
  ];

  for (const selector of selectors) {
    const element = document.querySelector(selector);
    if (element?.value) return String(element.value).toUpperCase();
  }

  return "ANY";
}

async function getNotesForQuiz() {
  try {
    const notes = await apiGet("/notes/" + state.topicId);

    if (Array.isArray(notes) && notes.length) {
      return notes.map(note => note.content || "").filter(Boolean).join("\n\n");
    }
  } catch (error) {
    console.warn("Could not load notes from backend.", error);
  }

  return DEMO.notes[state.topicId] || "";
}

// MCQ-ONLY QUESTION PREPARATION
function prepareQuestion(question, index) {
  const options = Array.isArray(question.options)
    ? question.options.filter(option =>
        option != null && String(option).trim() !== ""
      )
    : [];

  let answer = question.answer ?? question.correctAnswer ?? "";

  if (/^[A-D]$/i.test(String(answer).trim())) {
    const answerIndex =
      String(answer).trim().toUpperCase().charCodeAt(0) - 65;
    answer = options[answerIndex] ?? "";
  }

  const matchingOption = options.find(
    option =>
      String(option).trim().toLowerCase() ===
      String(answer).trim().toLowerCase()
  );

  if (matchingOption) answer = matchingOption;

  return {
    ...question,
    _index: index,
    type: "MCQ",
    options,
    answer,
    question: question.question ?? question.questionText ?? ""
  };
}

function validateGeneratedQuestion(question) {
  if (!String(question.question || "").trim()) {
    return "A generated question has no question text.";
  }

  if (question.options.length !== 4) {
    return "Every generated MCQ must have exactly four options.";
  }

  const uniqueOptions = new Set(
    question.options.map(normaliseAnswer)
  );

  if (uniqueOptions.size !== 4) {
    return "Every MCQ must have four different options.";
  }

  if (!String(question.answer || "").trim()) {
    return "A generated question has no correct answer.";
  }

  const matches = question.options.filter(
    option => normaliseAnswer(option) === normaliseAnswer(question.answer)
  );

  if (matches.length !== 1) {
    return "The correct answer must match one of the four options.";
  }

  return null;
}

// GENERATE QUIZ
const takeQuizButton = document.getElementById("takeQuizBtn");

if (takeQuizButton) {
  takeQuizButton.addEventListener("click", loadQuiz);
}

async function loadQuiz() {
  if (!state.topicId) {
    notify("Open a topic before starting a quiz.", "error");
    return;
  }

  showView("quiz");
  state.questions = [];
  state.quizSubmitted = false;

  setCrumbs([
    { label: "Subjects", onClick: loadSubjects },
    { label: state.subjectName, onClick: loadTopics },
    { label: state.topicName, onClick: loadTopicDetail },
    { label: "MCQ Quiz" }
  ]);

  const form = document.getElementById("quizForm");
  const submitButton = document.getElementById("submitQuizBtn");

  if (!form) return;

  form.innerHTML = `
    <div class="loading-state">
      <span class="spinner"></span>
      <span>Generating your MCQ quiz...</span>
    </div>
  `;

  if (submitButton) {
    submitButton.disabled = true;
    submitButton.textContent = "Loading...";
  }

  const count = getQuizCount();
  const difficulty = getDifficulty();
  const notes = await getNotesForQuiz();

  if (!notes || notes.trim().split(/\s+/).length < 40) {
    form.replaceChildren();
    showEmpty("Add at least about 40 words of notes before generating a quiz.");

    if (submitButton) {
      submitButton.disabled = true;
      submitButton.textContent = "Submit Quiz";
    }
    return;
  }

  try {
    // The Java backend must also generate MCQs only.
    // No question-type selection is sent from the frontend.
    const generated = await apiPost(
      "/quiz/generate",
      { notes, count, difficulty },
      30000
    );

    if (!Array.isArray(generated)) {
      throw new Error("The quiz generator returned an invalid response.");
    }

    const prepared = generated.map((question, index) =>
      prepareQuestion(question, index)
    );

    for (const question of prepared) {
      const problem = validateGeneratedQuestion(question);
      if (problem) throw new Error(problem);
    }

    state.questions = prepared.slice(0, count).map((question, index) => ({
      ...question,
      _index: index
    }));

    if (!state.questions.length) {
      throw new Error("No MCQs were generated. Try adding more detailed notes.");
    }

    const progress = document.getElementById("quizProgress");
    if (progress) {
      progress.textContent =
        `${state.questions.length} questions · Choose one answer for each`;
    }

    renderGeneratedQuiz();

    if (submitButton) {
      submitButton.disabled = false;
      submitButton.textContent = "Submit Quiz";
    }
  } catch (error) {
    console.error("Quiz generation failed:", error);

    form.innerHTML = `
      <div class="quiz-error">
        <h3>Could not generate the quiz</h3>
        <p>${escapeHtml(error.message || "Please try again.")}</p>
        <button type="button" id="retryQuizBtn" class="primary">
          Try Again
        </button>
      </div>
    `;

    $("#retryQuizBtn")?.addEventListener("click", loadQuiz);

    if (submitButton) {
      submitButton.disabled = true;
      submitButton.textContent = "Submit Quiz";
    }
  }
}

function renderGeneratedQuiz() {
  const form = document.getElementById("quizForm");
  if (!form) return;

  form.replaceChildren();

  state.questions.forEach((question, index) => {
    const block = document.createElement("section");
    block.className = "quiz-question generated-question";
    block.dataset.index = index;

    const optionsHtml = question.options.map((option, optionIndex) => `
      <label class="quiz-option">
        <input
          type="radio"
          name="generated-q-${index}"
          value="${optionIndex}"
        >
        <span class="option-letter">${String.fromCharCode(65 + optionIndex)}</span>
        <span>${escapeHtml(option)}</span>
      </label>
    `).join("");

    block.innerHTML = `
      <div class="generated-type">MCQ · Question ${index + 1}</div>
      <p class="qtext">${escapeHtml(question.question)}</p>
      <div class="generated-answer-area">${optionsHtml}</div>
      <div class="question-feedback hidden"></div>
    `;

    form.appendChild(block);
  });
}

function getUserAnswer(question, index) {
  const selected = document.querySelector(
    `input[name="generated-q-${index}"]:checked`
  );

  if (!selected) return "";

  return question.options[Number(selected.value)] ?? "";
}

function isAnswerCorrect(question, userAnswer) {
  return Boolean(userAnswer) &&
    normaliseAnswer(userAnswer) === normaliseAnswer(question.answer);
}

function revealAnswerForQuestion(block, question, correct) {
  const feedback = block.querySelector(".question-feedback");
  if (!feedback) return;

  feedback.classList.remove("hidden");
  feedback.className = correct
    ? "question-feedback correct"
    : "question-feedback wrong";

  feedback.innerHTML = correct
    ? "✓ Correct answer!"
    : `✗ Incorrect. <strong>Correct answer:</strong> ${escapeHtml(question.answer)}`;

  block.querySelectorAll(".quiz-option").forEach(label => {
    label.classList.remove(
      "selected-correct",
      "selected-wrong",
      "correct-option"
    );

    const input = label.querySelector("input");
    const option = question.options[Number(input.value)];

    if (normaliseAnswer(option) === normaliseAnswer(question.answer)) {
      label.classList.add("correct-option");
    }

    if (input.checked) {
      label.classList.add(correct ? "selected-correct" : "selected-wrong");
    }
  });
}

const submitQuizButton = document.getElementById("submitQuizBtn");

if (submitQuizButton) {
  submitQuizButton.addEventListener("click", () => {
    if (!state.questions.length || state.quizSubmitted) return;

    let score = 0;

    state.questions.forEach((question, index) => {
      const block = document.querySelector(
        `.generated-question[data-index="${index}"]`
      );

      if (!block) return;

      const answer = getUserAnswer(question, index);
      const correct = isAnswerCorrect(question, answer);

      if (correct) score++;

      revealAnswerForQuestion(block, question, correct);

      block.querySelectorAll("input").forEach(input => {
        input.disabled = true;
      });
    });

    const total = state.questions.length;
    const percentage = total ? (score / total) * 100 : 0;

    state.quizSubmitted = true;
    state.lastResult = {
      score,
      totalQuestions: total,
      percentage
    };

    submitQuizButton.disabled = true;
    submitQuizButton.textContent = `Score: ${score}/${total}`;

    const progress = document.getElementById("quizProgress");
    if (progress) {
      progress.textContent =
        `Quiz completed · ${score}/${total} correct (${Math.round(percentage)}%)`;
    }

    notify(`Quiz completed: ${score}/${total} correct.`, "success");

    // This only displays the result locally.
    // Persistent history requires a compatible backend submit endpoint.
  });
}

// RESULTS
function showResult(result) {
  showView("result");
  setCrumbs([{ label: "Result" }]);

  const score = document.getElementById("resultScore");
  const percentage = document.getElementById("resultPct");

  if (score) {
    score.textContent = `${result.score} / ${result.totalQuestions}`;
  }

  if (percentage) {
    percentage.textContent = `${Math.round(result.percentage)}%`;
  }
}

document.getElementById("backToTopicBtn")?.addEventListener(
  "click",
  loadTopicDetail
);

document.getElementById("resultViewHistoryBtn")?.addEventListener(
  "click",
  loadHistory
);

// HISTORY
document.getElementById("viewHistoryBtn")?.addEventListener(
  "click",
  loadHistory
);

document.getElementById("historyBackBtn")?.addEventListener(
  "click",
  loadTopicDetail
);

async function loadHistory() {
  showView("history");
  setCrumbs([{ label: "Quiz History" }]);

  const list = document.getElementById("historyList");
  if (!list) return;

  setLoading(list, "Loading quiz history...");

  try {
    const studentId = Number(studentIdInput?.value) || 1;
    const attempts = await apiGet("/quiz/history/" + studentId);

    list.replaceChildren();

    if (!Array.isArray(attempts) || !attempts.length) {
      showEmpty("No quiz attempts yet. Complete a quiz to see your progress.");
      return;
    }

    attempts.slice().reverse().forEach(attempt => {
      const percentage = Math.round(Number(attempt.percentage) || 0);
      const row = document.createElement("div");
      row.className = "history-row";

      row.innerHTML = `
        <span>Attempt #${escapeHtml(attempt.attemptId)} ·
          ${escapeHtml(attempt.score)}/${escapeHtml(attempt.totalQuestions)}
        </span>
        <span class="${percentage >= 50 ? "score-good" : "score-bad"}">
          ${percentage}%
        </span>
      `;

      list.appendChild(row);
    });
  } catch (error) {
    list.replaceChildren();
    showEmpty("Could not load quiz history. Please check the backend connection.");
  }
}

// OPTIONAL SEARCH: searches rendered subjects or topics.
function attachListSearch(inputSelector, listSelector) {
  const input = document.querySelector(inputSelector);
  const list = document.querySelector(listSelector);

  if (!input || !list) return;

  input.addEventListener("input", () => {
    const query = input.value.trim().toLowerCase();

    list.querySelectorAll(".item-card").forEach(card => {
      card.classList.toggle(
        "search-hidden",
        !card.textContent.toLowerCase().includes(query)
      );
    });
  });
}

attachListSearch("#subjectSearch", "#subjectList");
attachListSearch("#topicSearch", "#topicList");

// START APP
loadSubjects();
