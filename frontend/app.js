const studentForm = document.getElementById('studentForm');
const tableBody = document.getElementById('studentTableBody');
const refreshBtn = document.getElementById('refreshBtn');
const logoutBtn = document.getElementById('logoutBtn');
const cancelEditBtn = document.getElementById('cancelEdit');
const formTitle = document.getElementById('formTitle');
const totalStudents = document.getElementById('totalStudents');
const courseCount = document.getElementById('courseCount');
const latestStudent = document.getElementById('latestStudent');
const searchInput = document.getElementById('searchInput');
const clearSearch = document.getElementById('clearSearch');
const themeToggle = document.getElementById('themeToggle');

function debounce(fn, wait) {
  let t;
  return (...args) => {
    clearTimeout(t);
    t = setTimeout(() => fn(...args), wait);
  };
}

async function loadStudents(q) {
  const url = q ? `/api/students?q=${encodeURIComponent(q)}` : '/api/students';
  const response = await fetch(url);
  const students = await response.json();

  tableBody.innerHTML = '';

  if (!students.length) {
    tableBody.innerHTML = '<tr><td colspan="5">No students found.</td></tr>';
    totalStudents.textContent = '0';
    courseCount.textContent = '0';
    latestStudent.textContent = '-';
    return;
  }

  const uniqueCourses = new Set(students.map((student) => student.course.trim()).filter(Boolean));
  totalStudents.textContent = students.length;
  courseCount.textContent = uniqueCourses.size;
  latestStudent.textContent = students[students.length - 1].name;

  students.forEach((student) => {
    const row = document.createElement('tr');
    row.innerHTML = `
      <td>${student.id}</td>
      <td>${student.name}</td>
      <td>${student.email}</td>
      <td>${student.course}</td>
      <td>
        <div class="action-group">
          <button class="secondary-btn" data-action="edit" data-id="${student.id}">Edit</button>
          <button class="delete-btn" data-action="delete" data-id="${student.id}">Delete</button>
        </div>
      </td>
    `;
    tableBody.appendChild(row);
  });
}

studentForm.addEventListener('submit', async (event) => {
  event.preventDefault();

  const id = document.getElementById('studentId').value;
  const payload = {
    name: document.getElementById('name').value.trim(),
    email: document.getElementById('email').value.trim(),
    course: document.getElementById('course').value.trim(),
  };

  if (!payload.name || !payload.email || !payload.course) {
    alert('Please fill in all fields');
    return;
  }

  const url = id ? `/api/students/${id}` : '/api/students';
  const method = id ? 'PUT' : 'POST';

  const response = await fetch(url, {
    method,
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  const result = await response.json();

  if (!response.ok) {
    alert(result.error || 'Something went wrong');
    return;
  }
  // finished

  alert(result.message || 'Student saved successfully');
  studentForm.reset();
  clearEditState();
  loadStudents();
});

tableBody.addEventListener('click', async (event) => {
  const target = event.target.closest('button');
  if (!target) {
    return;
  }

  const studentId = target.dataset.id;
  const action = target.dataset.action;

  if (action === 'delete') {
    if (!confirm('Delete this student?')) {
      return;
    }

    const response = await fetch(`/api/students/${studentId}`, { method: 'DELETE' });
    const result = await response.json();

    if (!response.ok) {
      alert(result.error || 'Delete failed');
      return;
    }

    alert(result.message || 'Student deleted');
    loadStudents();
  }

  if (action === 'edit') {
    const response = await fetch(`/api/students/${studentId}`);
    const student = await response.json();

    if (!response.ok) {
      alert(student.error || 'Student not found');
      return;
    }

    document.getElementById('studentId').value = student.id;
    document.getElementById('name').value = student.name;
    document.getElementById('email').value = student.email;
    document.getElementById('course').value = student.course;
    // photo field removed from UI
    formTitle.textContent = 'Edit Student';
    cancelEditBtn.classList.remove('hidden');
    document.getElementById('name').focus();
  }
});

cancelEditBtn.addEventListener('click', () => {
  clearEditState();
  studentForm.reset();
});

function clearEditState() {
  document.getElementById('studentId').value = '';
  formTitle.textContent = 'Add Student';
  cancelEditBtn.classList.add('hidden');
}

refreshBtn.addEventListener('click', loadStudents);
loadStudents();

if (searchInput) {
  const runSearch = debounce(() => loadStudents(searchInput.value.trim()), 300);
  searchInput.addEventListener('input', runSearch);
  clearSearch.addEventListener('click', () => {
    searchInput.value = '';
    loadStudents();
  });
}

if (logoutBtn) {
  logoutBtn.addEventListener('click', async () => {
    try { localStorage.removeItem('auth'); } catch(e){}
    window.location.href = '/login.html';
  });
}

// Theme handling
function applyTheme(theme) {
  if (theme === 'dark') {
    document.documentElement.classList.add('dark');
    if (themeToggle) themeToggle.classList.add('is-dark');
  } else {
    document.documentElement.classList.remove('dark');
    if (themeToggle) themeToggle.classList.remove('is-dark');
  }
}

function toggleTheme() {
  const current = localStorage.getItem('theme') || (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  const next = current === 'dark' ? 'light' : 'dark';
  localStorage.setItem('theme', next);
  applyTheme(next);
}

// Initialize theme: prefer saved setting, otherwise respect system preference
const savedTheme = localStorage.getItem('theme');
const initialTheme = savedTheme || (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
applyTheme(initialTheme);

if (themeToggle) {
  themeToggle.addEventListener('click', toggleTheme);
}
