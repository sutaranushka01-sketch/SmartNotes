const API_BASE = "/api";

// ---- simple state ----
let state = {
  subjectId: null,
  subjectName: "",
  topicId: null,
  topicName: "",
  questions: [],      // current quiz questions (no correct answers, from server)
  lastResult: null
};

// ================= DEMO DATA =================
// Used automatically whenever the backend isn't reachable, so the app
// always looks fully populated. Once your Spring Boot backend is running,
// real data from MySQL takes over automatically instead.
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

async function apiGet(path) {
  const res = await fetch(API_BASE + path, { signal: AbortSignal.timeout(1500) });
  if (!res.ok) throw new Error("GET " + path + " failed: " + res.status);
  return res.json();
}

async function apiPost(path, body) {
  const res = await fetch(API_BASE + path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(1500)
  });
  if (!res.ok) throw new Error("POST " + path + " failed: " + res.status);
  return res.json();
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
  } catch (e) {
    subjects = DEMO.subjects; // backend not reachable — show sample data
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
  const nameInput = document.getElementById("newSubjectName");
  try {
    await apiPost("/subjects", { subjectName: nameInput.value });
  } catch (err) { /* demo mode — ignore */ }
  nameInput.value = "";
  loadSubjects();
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
  const nameInput = document.getElementById("newTopicName");
  try {
    await apiPost("/topics/" + state.subjectId, { topicName: nameInput.value });
  } catch (err) { /* demo mode — ignore */ }
  nameInput.value = "";
  loadTopics();
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
    notesEl.textContent = notes.length ? notes.map(n => n.content).join("\n\n") : (DEMO.notes[state.topicId] || "No notes added yet for this topic.");
  } catch (e) {
    notesEl.textContent = DEMO.notes[state.topicId] || "No notes added yet for this topic.";
  }
}

document.getElementById("addNotesForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const contentInput = document.getElementById("newNotesContent");
  try {
    await apiPost("/notes/" + state.topicId, { content: contentInput.value });
  } catch (err) { /* demo mode — ignore */ }
  contentInput.value = "";
  loadTopicDetail();
});

document.getElementById("addQuestionForm").addEventListener("submit", async (e) => {
  e.preventDefault();
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
  } catch (err) { /* demo mode — ignore */ }
  e.target.reset();
  alert("Question added.");
});

// ================= QUIZ =================
document.getElementById("takeQuizBtn").addEventListener("click", loadQuiz);

async function loadQuiz() {
  showView("quiz");
  setCrumbs([
    { label: "Subjects", onClick: loadSubjects },
    { label: state.subjectName, onClick: loadTopics },
    { label: state.topicName, onClick: loadTopicDetail },
    { label: "Quiz" }
  ]);
  const form = document.getElementById("quizForm");
  form.innerHTML = "Loading questions...";

  let questions;
  try {
    questions = await apiGet("/questions/" + state.topicId);
    if (!questions.length) questions = DEMO.questions[state.topicId] || [];
  } catch (e) {
    questions = DEMO.questions[state.topicId] || [];
  }
  state.questions = questions;

  document.getElementById("quizProgress").textContent =
    questions.length + " question" + (questions.length === 1 ? "" : "s") + " — choose one answer each";

  if (questions.length === 0) {
    form.innerHTML = "";
    showEmpty("No questions added for this topic yet.");
    return;
  }

  form.innerHTML = "";
  questions.forEach((q, idx) => {
    const block = document.createElement("div");
    block.className = "quiz-question";
    block.innerHTML = `
      <p class="qtext">${idx + 1}. ${escapeHtml(q.questionText)}</p>
      ${["A","B","C","D"].map(opt => `
        <label>
          <input type="radio" name="q${q.questionId}" value="${opt}">
          ${escapeHtml(q["option" + opt])}
        </label>
      `).join("")}
    `;
    form.appendChild(block);
  });
}

document.getElementById("submitQuizBtn").addEventListener("click", async () => {
  const answers = state.questions.map(q => {
    const selected = document.querySelector(`input[name="q${q.questionId}"]:checked`);
    return {
      questionId: q.questionId,
      selectedOption: selected ? selected.value : ""
    };
  });

  const unanswered = answers.filter(a => a.selectedOption === "").length;
  if (unanswered > 0 && !confirm(unanswered + " question(s) left unanswered. Submit anyway?")) {
    return;
  }

  const submission = {
    studentId: Number(studentIdInput.value) || 1,
    topicId: state.topicId,
    answers
  };

  try {
    const result = await apiPost("/quiz/submit", submission);
    state.lastResult = result;
    showResult(result);
  } catch (e) {
    // demo mode — fake a plausible score locally just for display purposes
    const total = answers.length;
    const answered = answers.filter(a => a.selectedOption !== "").length;
    const demoScore = Math.max(1, Math.round(answered * 0.7));
    showResult({
      score: demoScore,
      totalQuestions: total,
      percentage: total ? (demoScore / total) * 100 : 0
    });
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

// ---- small helper to avoid raw HTML injection from notes/questions text ----
function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str ?? "";
  return div.innerHTML;
}

// ---- boot ----
loadSubjects();
