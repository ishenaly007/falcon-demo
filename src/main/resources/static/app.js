/*
 * WorkTime — фронтенд без сборки и фреймворков.
 * Состояние живёт на сервере; страница лишь показывает его и раз в секунду
 * "докручивает" идущие таймеры локально, не дёргая API.
 */
(() => {
    'use strict';

    const TZ = Intl.DateTimeFormat().resolvedOptions().timeZone;
    const $ = (id) => document.getElementById(id);

    /** Записей отчёта на одной странице. */
    const ENTRIES_PAGE = 10;

    const state = {
        employees: [],
        summary: null,
        entries: [],
        entriesPage: 0,          // текущая страница списка записей, с нуля
        today: null,
        preset: 'week',
        fetchedAt: Date.now(),   // момент последней загрузки — от него считаем "живые" секунды
    };

    // ---------- API ----------

    /** Сервер недоступен (остановлен, нет сети) — в отличие от ошибки, которую вернул сам сервер. */
    class OfflineError extends Error {
        constructor() {
            super('Нет связи с сервером');
            this.offline = true;
        }
    }

    async function api(method, url, body) {
        let res;
        try {
            res = await fetch(url, {
                method,
                headers: body ? { 'Content-Type': 'application/json' } : {},
                body: body ? JSON.stringify(body) : undefined,
            });
        } catch {
            // fetch падает с TypeError "Failed to fetch", только если запрос вообще не дошёл до сервера
            throw new OfflineError();
        }
        setOffline(false);
        syncClock(res.headers.get('Date'));
        const data = res.status === 204 ? null : await res.json().catch(() => null);
        if (!res.ok) {
            throw new Error((data && data.detail) || `Ошибка сервера (${res.status})`);
        }
        return data;
    }

    const qs = (params) => new URLSearchParams(
        Object.entries(params).filter(([, v]) => v !== '' && v != null)).toString();

    // ---------- Даты и форматирование ----------

    const pad = (n) => String(n).padStart(2, '0');
    const isoDate = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

    function presetRange(preset) {
        const now = new Date();
        const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
        if (preset === 'today') return [today, today];
        if (preset === 'month') {
            return [new Date(today.getFullYear(), today.getMonth(), 1),
                new Date(today.getFullYear(), today.getMonth() + 1, 0)];
        }
        // неделя: понедельник–воскресенье
        const monday = new Date(today);
        monday.setDate(today.getDate() - ((today.getDay() + 6) % 7));
        const sunday = new Date(monday);
        sunday.setDate(monday.getDate() + 6);
        return [monday, sunday];
    }

    function fmtDuration(sec) {
        sec = Math.max(0, Math.floor(sec));
        const h = Math.floor(sec / 3600);
        const m = Math.floor((sec % 3600) / 60);
        if (h === 0 && m === 0) return sec > 0 ? '< 1 мин' : '0 мин';
        return h > 0 ? `${h} ч ${pad(m)} мин` : `${m} мин`;
    }

    function fmtClock(sec) {
        sec = Math.max(0, Math.floor(sec));
        return `${pad(Math.floor(sec / 3600))}:${pad(Math.floor((sec % 3600) / 60))}:${pad(sec % 60)}`;
    }

    const dayFmt = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'short' });
    const timeFmt = new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit' });

    /** Две строки для ячейки "Время": день(и) и интервал часов. */
    function fmtRange(startIso, endIso) {
        const s = new Date(startIso);
        const e = endIso ? new Date(endIso) : null;
        const sameDay = !e || isoDate(s) === isoDate(e);
        return {
            day: sameDay ? dayFmt.format(s) : `${dayFmt.format(s)} → ${dayFmt.format(e)}`,
            time: `${timeFmt.format(s)} — ${e ? timeFmt.format(e) : 'сейчас'}`,
        };
    }

    function initials(name) {
        return name.trim().split(/\s+/).slice(0, 2).map((p) => p[0]).join('').toUpperCase();
    }

    function avatarColor(seed) {
        let h = 0;
        for (const ch of String(seed)) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
        return `hsl(${h % 360} 58% 52%)`;
    }

    /*
     * Таймер идёт от startedAt, выданного сервером, а "сейчас" берётся из часов браузера.
     * Если часы компьютера сбиты, таймер показывал бы ерунду (или 00:00:00 "из будущего"),
     * поэтому учитываем расхождение по заголовку Date ответа. Точность заголовка — секунда,
     * так что поправку применяем только при заметном расхождении.
     */
    let clockSkewMs = 0;

    function syncClock(dateHeader) {
        const server = dateHeader ? Date.parse(dateHeader) : NaN;
        if (Number.isNaN(server)) return;
        const skew = server - Date.now();
        clockSkewMs = Math.abs(skew) > 2000 ? skew : 0;
    }

    const serverNow = () => Date.now() + clockSkewMs;
    const secondsSince = (iso) => (serverNow() - new Date(iso).getTime()) / 1000;
    const liveDelta = () => (Date.now() - state.fetchedAt) / 1000;

    // ---------- Связь с сервером ----------

    /*
     * Если сервер остановлен, вместо потока одинаковых тостов показываем одну плашку
     * и пробуем переподключиться каждые 5 секунд. Как только сервер ответил, плашка исчезает.
     */
    let retryTimer = null;

    function setOffline(offline) {
        $('offline').hidden = !offline;
        if (offline && !retryTimer) {
            retryTimer = setInterval(refresh, 5000);
        } else if (!offline && retryTimer) {
            clearInterval(retryTimer);
            retryTimer = null;
        }
    }

    function reportError(e) {
        if (e.offline) setOffline(true);
        else toast(e.message, 'error');
    }

    // ---------- Уведомления ----------

    function toast(message, type = 'info') {
        const el = document.createElement('div');
        el.className = `toast ${type === 'error' ? 'error' : ''}`;
        el.textContent = message;
        $('toasts').append(el);
        setTimeout(() => el.remove(), type === 'error' ? 5000 : 2600);
    }

    // ---------- Загрузка ----------

    function period() {
        return { from: $('from').value, to: $('to').value };
    }

    async function refresh() {
        const { from, to } = period();
        const todayIso = isoDate(new Date());
        const employeeId = $('employeeFilter').value;
        try {
            const [employees, summary, entries, today] = await Promise.all([
                api('GET', '/api/employees'),
                api('GET', `/api/reports/summary?${qs({ from, to, tz: TZ })}`),
                api('GET', `/api/reports/entries?${qs({ from, to, employeeId, tz: TZ })}`),
                api('GET', `/api/reports/summary?${qs({ from: todayIso, to: todayIso, tz: TZ })}`),
            ]);
            Object.assign(state, { employees, summary, entries, today, fetchedAt: Date.now() });
            render();
        } catch (e) {
            reportError(e);
        }
    }

    // ---------- Отрисовка ----------

    function render() {
        renderEmployees();
        renderFilter();
        renderReport();
        tick();
    }

    function renderEmployees() {
        const list = $('employees');
        const tpl = $('employeeTpl');
        // запоминаем недописанные описания задач, чтобы перерисовка их не стирала
        const drafts = new Map([...list.querySelectorAll('.employee')].map((li) =>
            [li.dataset.id, li.querySelector('.timer-form input').value]));
        const focusedId = document.activeElement?.closest?.('.employee')?.dataset.id;

        list.replaceChildren(...state.employees.map((emp) => {
            const li = tpl.content.firstElementChild.cloneNode(true);
            li.dataset.id = emp.id;
            li.classList.toggle('is-running', !!emp.activeTimer);

            const avatar = li.querySelector('.avatar');
            avatar.textContent = initials(emp.name);
            avatar.style.background = avatarColor(emp.name + emp.id);
            li.querySelector('.emp-name').textContent = emp.name;
            li.querySelector('.emp-position').textContent = emp.position || 'Без должности';
            li.querySelector('.emp-status').textContent = emp.activeTimer ? 'Работает' : 'Не в работе';

            const input = li.querySelector('.timer-form input');
            input.value = drafts.get(String(emp.id)) || '';
            li.querySelector('.timer-form').addEventListener('submit', (ev) => {
                ev.preventDefault();
                startTimer(emp, input, ev.submitter);
            });

            if (emp.activeTimer) {
                li.querySelector('.running-task').textContent = emp.activeTimer.description;
                const clock = li.querySelector('.clock');
                clock.dataset.since = emp.activeTimer.startedAt;
                li.querySelector('.btn-stop').addEventListener('click', (ev) => stopTimer(emp, ev.currentTarget));
            }
            return li;
        }));

        if (focusedId) list.querySelector(`.employee[data-id="${focusedId}"] .timer-form input`)?.focus();

        const n = state.employees.length;
        const active = state.employees.filter((e) => e.activeTimer).length;
        $('employeesEmpty').hidden = n > 0;
        $('teamCount').textContent = n ? `${n} чел.` : '';
        $('statEmployees').textContent = n;
        $('statActive').textContent = active;
        $('statLiveDot').classList.toggle('on', active > 0);
    }

    function renderFilter() {
        const select = $('employeeFilter');
        const current = select.value;
        select.replaceChildren(new Option('Все сотрудники', ''),
            ...state.employees.map((e) => new Option(e.name, e.id)));
        select.value = state.employees.some((e) => String(e.id) === current) ? current : '';
    }

    /** Период содержит "сейчас" — значит идущие таймеры должны тикать и в отчёте. */
    function periodIsLive() {
        const today = isoDate(new Date());
        const { from, to } = period();
        return from <= today && today <= to;
    }

    function renderReport() {
        const s = state.summary;
        if (!s) return;
        const live = periodIsLive();
        const runningCount = s.employees.filter((e) => e.running).length;

        const total = $('totalValue');
        total.dataset.base = s.totalSeconds;
        total.dataset.rate = live ? runningCount : 0;
        $('totalMeta').textContent = `${s.entryCount} ${plural(s.entryCount, 'запись', 'записи', 'записей')}`;
        $('reportZone').textContent = `пояс: ${s.timeZone}`;

        $('bars').replaceChildren(...s.employees.map((e) => {
            const li = document.createElement('li');
            li.className = 'bar-row' + (e.totalSeconds === 0 && !e.running ? ' zero' : '');
            li.innerHTML = `
                <div class="bar-label">
                    <span><span class="name"></span></span>
                    <span class="value"></span>
                </div>
                <div class="bar-track"><div class="bar-fill"></div></div>`;
            li.querySelector('.name').textContent = e.name;
            if (e.running && live) {
                const badge = document.createElement('span');
                badge.className = 'badge-live';
                badge.textContent = '● в работе';
                li.querySelector('.name').after(badge);
            }
            const value = li.querySelector('.value');
            value.dataset.base = e.totalSeconds;
            value.dataset.rate = e.running && live ? 1 : 0;
            return li;
        }));

        // За месяц записей могут быть сотни — показываем постранично
        const pages = Math.max(1, Math.ceil(state.entries.length / ENTRIES_PAGE));
        state.entriesPage = Math.min(state.entriesPage, pages - 1); // после обновления страниц могло стать меньше
        const first = state.entriesPage * ENTRIES_PAGE;
        $('entries').replaceChildren(...state.entries.slice(first, first + ENTRIES_PAGE).map((e) => {
            const tr = document.createElement('tr');
            tr.innerHTML = `<td class="who"></td><td class="task"></td><td class="when"></td><td class="num"></td>`;
            tr.querySelector('.who').textContent = e.employeeName;
            tr.querySelector('.task').textContent = e.description;
            const range = fmtRange(e.startedAt, e.endedAt);
            const when = tr.querySelector('.when');
            when.innerHTML = '<span class="when-day"></span><span class="when-time"></span>';
            when.querySelector('.when-day').textContent = range.day;
            when.querySelector('.when-time').textContent = range.time;
            const dur = tr.querySelector('.num');
            if (e.running) {
                dur.innerHTML = '<span class="v"></span><span class="pill-live">идёт</span>';
                const v = dur.querySelector('.v');
                v.dataset.base = e.durationSeconds;
                v.dataset.rate = 1;
            } else {
                dur.textContent = fmtDuration(e.durationSeconds);
                if (e.secondsInPeriod < e.durationSeconds) {
                    const part = document.createElement('span');
                    part.className = 'part';
                    part.textContent = `в периоде: ${fmtDuration(e.secondsInPeriod)}`;
                    dur.append(part);
                }
            }
            return tr;
        }));

        const entryCount = state.entries.length;
        const noEntries = entryCount === 0;
        $('reportEmpty').hidden = !noEntries;
        document.querySelector('.table-wrap').hidden = noEntries;
        $('entriesCount').textContent = noEntries ? '' : String(entryCount);
        renderPager(entryCount, pages);
    }

    /**
     * Номера страниц: первая, последняя и соседние с текущей, между ними «…».
     * Пример для 13 страниц, текущая 6: 1 … 5 6 7 … 13
     */
    function pageNumbers(current, pages) {
        const keep = new Set([0, pages - 1, current - 1, current, current + 1]);
        const result = [];
        for (let i = 0; i < pages; i++) {
            if (keep.has(i)) result.push(i);
            else if (result[result.length - 1] !== '…') result.push('…');
        }
        return result;
    }

    function renderPager(total, pages) {
        const pager = $('pager');
        pager.hidden = pages <= 1;
        if (pages <= 1) return;

        const current = state.entriesPage;
        const button = (label, page, { disabled = false, active = false, aria } = {}) => {
            const b = document.createElement('button');
            b.type = 'button';
            b.textContent = label;
            b.dataset.page = page;
            b.disabled = disabled;
            if (active) b.setAttribute('aria-current', 'page');
            if (aria) b.setAttribute('aria-label', aria);
            return b;
        };

        const from = current * ENTRIES_PAGE + 1;
        const to = Math.min(total, from + ENTRIES_PAGE - 1);
        const info = document.createElement('span');
        info.className = 'pager-info';
        info.textContent = `${from}–${to} из ${total}`;

        const nums = document.createElement('div');
        nums.className = 'pager-pages';
        nums.append(
            button('‹', current - 1, { disabled: current === 0, aria: 'Предыдущая страница' }),
            ...pageNumbers(current, pages).map((p) => {
                if (p !== '…') return button(String(p + 1), p, { active: p === current });
                const gap = document.createElement('span');
                gap.className = 'pager-gap';
                gap.textContent = '…';
                return gap;
            }),
            button('›', current + 1, { disabled: current === pages - 1, aria: 'Следующая страница' }));

        pager.replaceChildren(info, nums);
    }

    $('pager').addEventListener('click', (ev) => {
        const b = ev.target.closest('button[data-page]');
        if (!b || b.disabled) return;
        state.entriesPage = Number(b.dataset.page);
        renderReport();
        tick();
        document.querySelector('.sub-title').scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    });

    /** Новый период или фильтр — список снова с первой страницы. */
    function resetEntriesPaging() {
        state.entriesPage = 0;
    }

    function plural(n, one, few, many) {
        const m10 = n % 10, m100 = n % 100;
        if (m10 === 1 && m100 !== 11) return one;
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
        return many;
    }

    /** Раз в секунду: часы в шапке, таймеры сотрудников и "живые" суммы. */
    function tick() {
        const now = new Date();
        $('now').textContent = `${dayFmt.format(now)}, ${now.toLocaleTimeString('ru-RU')}`;

        document.querySelectorAll('.clock[data-since]').forEach((el) => {
            el.textContent = fmtClock(secondsSince(el.dataset.since));
        });

        const delta = liveDelta();
        const live = (el) => Number(el.dataset.base) + Number(el.dataset.rate || 0) * delta;
        document.querySelectorAll('[data-base]').forEach((el) => {
            el.textContent = fmtDuration(live(el));
        });

        // ширина полос считается от текущего лидера, поэтому пересчитываем вместе с секундами
        const rows = [...document.querySelectorAll('#bars .bar-row')];
        const values = rows.map((row) => live(row.querySelector('.value')));
        const max = Math.max(1, ...values);
        rows.forEach((row, i) => {
            row.querySelector('.bar-fill').style.width = `${(values[i] / max) * 100}%`;
        });

        if (state.today) {
            const running = state.today.employees.filter((e) => e.running).length;
            $('statToday').textContent = fmtDuration(state.today.totalSeconds + running * delta);
        }
    }

    // ---------- Действия ----------

    async function withBusy(button, fn) {
        if (button) button.disabled = true;
        try {
            await fn();
        } catch (e) {
            reportError(e);
        } finally {
            if (button) button.disabled = false;
        }
    }

    function startTimer(emp, input, button) {
        const description = input.value.trim();
        if (!description) {
            input.focus();
            toast('Опишите задачу, над которой идёт работа', 'error');
            return;
        }
        return withBusy(button, async () => {
            await api('POST', `/api/employees/${emp.id}/timer/start`, { description });
            input.value = '';
            toast(`Таймер запущен: ${emp.name}`);
            await refresh();
        });
    }

    function stopTimer(emp, button) {
        return withBusy(button, async () => {
            const entry = await api('POST', `/api/employees/${emp.id}/timer/stop`);
            toast(`${emp.name}: +${fmtDuration(entry.durationSeconds)}`);
            await refresh();
        });
    }

    $('addForm').addEventListener('submit', (ev) => {
        ev.preventDefault();
        const name = $('empName').value.trim();
        const position = $('empPosition').value.trim();
        if (!name) {
            $('empName').focus();
            return;
        }
        withBusy(ev.submitter, async () => {
            await api('POST', '/api/employees', { name, position });
            ev.target.reset();
            $('empName').focus();
            toast(`Добавлен сотрудник: ${name}`);
            await refresh();
        });
    });

    // ---------- Период ----------

    function setPreset(preset) {
        state.preset = preset;
        resetEntriesPaging();
        document.querySelectorAll('#presets button').forEach((b) =>
            b.setAttribute('aria-selected', String(b.dataset.preset === preset)));
        if (preset !== 'custom') {
            const [from, to] = presetRange(preset);
            $('from').value = isoDate(from);
            $('to').value = isoDate(to);
        }
        try { localStorage.setItem('worktime.preset', preset); } catch { /* приватный режим — не страшно */ }
    }

    $('presets').addEventListener('click', (ev) => {
        const btn = ev.target.closest('button[data-preset]');
        if (!btn) return;
        setPreset(btn.dataset.preset);
        if (btn.dataset.preset === 'custom') $('from').focus(); else refresh();
    });

    ['from', 'to'].forEach((id) => $(id).addEventListener('change', () => {
        setPreset('custom');
        if ($('from').value && $('to').value) refresh();
    }));
    $('employeeFilter').addEventListener('change', () => {
        resetEntriesPaging();
        refresh();
    });

    // ---------- Тема ----------

    function applyTheme(theme) {
        if (theme) document.documentElement.dataset.theme = theme;
        else delete document.documentElement.dataset.theme;
    }

    $('themeToggle').addEventListener('click', () => {
        const dark = document.documentElement.dataset.theme
            ? document.documentElement.dataset.theme === 'dark'
            : matchMedia('(prefers-color-scheme: dark)').matches;
        const next = dark ? 'light' : 'dark';
        applyTheme(next);
        try { localStorage.setItem('worktime.theme', next); } catch { /* ignore */ }
    });

    // ---------- Старт ----------

    // ?period=today|week|month — можно поделиться ссылкой на нужный отчёт
    const PRESETS = ['today', 'week', 'month'];
    const urlPreset = new URLSearchParams(location.search).get('period');
    let savedPreset = null;
    try {
        applyTheme(localStorage.getItem('worktime.theme'));
        savedPreset = localStorage.getItem('worktime.preset');
    } catch { /* localStorage недоступен — работаем с настройками по умолчанию */ }
    setPreset([urlPreset, savedPreset].find((p) => PRESETS.includes(p)) || 'week');

    refresh();
    setInterval(tick, 1000);
    // синхронизация с другими вкладками/пользователями
    setInterval(() => { if (!document.hidden) refresh(); }, 30000);
    document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
})();
