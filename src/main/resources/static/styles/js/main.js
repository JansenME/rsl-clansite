// src/main/resources/static/js/main.js

let championCache = []; // Stores objects { el, name, score, ... }
let filteredCache = []; // Currently filtered list of objects
let visibleCount = 0;
const BATCH_SIZE = 24;

// --- Cache Initialization ---
function initChampionCache() {
    const cards = document.querySelectorAll('.champion-card');
    championCache = Array.from(cards).map(card => ({
        el: card,
        name: (card.getAttribute('data-name') || '').toLowerCase(),
        score: parseFloat(card.getAttribute('data-score')) || 0,
        rarity: (card.getAttribute('data-rarity') || '').trim(),
        type: (card.getAttribute('data-type') || '').trim(),
        affinity: (card.getAttribute('data-affinity') || '').trim(),
        faction: (card.getAttribute('data-faction') || '').trim(),
        alliance: (card.getAttribute('data-alliance') || '').trim()
    }));
    // Initially, all are filtered
    filteredCache = [...championCache];
}

function setupStarListeners() {
    // ... (No change to star listeners logic) ...
    const ratingContainer = document.getElementById('arena-score-rating');
    const finalInput = document.getElementById('finalArenaScore');

    if (!ratingContainer || !finalInput) return;

    ratingContainer.addEventListener('mousemove', (event) => {
        const hoveredLabel = event.target;
        ratingContainer.querySelectorAll('label').forEach(lbl => {
            lbl.classList.remove('half-star-hover');
            const originalTitle = lbl.getAttribute('data-original-title');
            if (originalTitle) lbl.setAttribute('title', originalTitle);
        });
        if (hoveredLabel.tagName !== 'LABEL') return;

        const rect = hoveredLabel.getBoundingClientRect();
        const clickX = event.clientX - rect.left;
        const fullScoreMatch = hoveredLabel.getAttribute('onclick').match(/setScore\((\d+\.\d)/);
        if (!fullScoreMatch) return;

        const fullScore = parseFloat(fullScoreMatch[1]);
        const originalTitle = hoveredLabel.getAttribute('title');
        if (!hoveredLabel.hasAttribute('data-original-title')) {
            hoveredLabel.setAttribute('data-original-title', originalTitle);
        }

        if (clickX < rect.width / 2) {
            hoveredLabel.classList.add('half-star-hover');
            const halfScore = (fullScore - 0.5).toFixed(1);
            hoveredLabel.setAttribute('title', `${halfScore} Stars`);
        }
    });

    ratingContainer.addEventListener('mouseenter', () => {
        ratingContainer.classList.remove('half-score-visual');
        ratingContainer.querySelectorAll('label').forEach(lbl => {
            lbl.classList.remove('checked-star');
            const originalTitle = lbl.getAttribute('data-original-title');
            if (originalTitle) lbl.setAttribute('title', originalTitle);
        });
    });

    ratingContainer.addEventListener('mouseleave', () => {
        ratingContainer.querySelectorAll('label').forEach(lbl => lbl.classList.remove('half-star-hover'));
        const currentScore = parseFloat(finalInput.value);
        if (!isNaN(currentScore)) updateVisuals(currentScore);
    });
}

function setScore(fullScore, clickEvent) {
    const finalInput = document.getElementById('finalArenaScore');
    const ratingContainer = document.getElementById('arena-score-rating');
    if (!finalInput || !ratingContainer) return;
    let finalScore = fullScore;
    if (clickEvent) {
        const label = clickEvent.currentTarget;
        const rect = label.getBoundingClientRect();
        const clickX = event.clientX - rect.left;
        if (clickX < rect.width / 2) finalScore = fullScore - 0.5;
    }
    finalScore = Math.max(1.0, Math.min(5.0, finalScore));
    finalInput.value = finalScore.toFixed(1);
    updateVisuals(finalScore);
}

function updateVisuals(score) {
    const ratingContainer = document.getElementById('arena-score-rating');
    const labels = ratingContainer ? ratingContainer.querySelectorAll('label') : [];
    if (!ratingContainer || labels.length === 0) return;
    let integerScore = Math.floor(score);
    if (score % 1 !== 0) integerScore += 1;
    const anchorId = 'score-' + (integerScore * 10).toFixed(0);
    const anchorLabel = document.querySelector(`label[for="${anchorId}"]`);
    labels.forEach(lbl => lbl.classList.remove('checked-star'));
    if (anchorLabel) anchorLabel.classList.add('checked-star');
    ratingContainer.classList.remove('half-score-visual');
    if (score % 1 !== 0) ratingContainer.classList.add('half-score-visual');
}

// --- OPTIMIZED MAIN FILTER LOGIC ---
function applyChampionFilters() {
    if (championCache.length === 0) initChampionCache();

    const startTime = performance.now();
    const searchInput = document.getElementById('champion-search');
    const searchTerm = searchInput ? searchInput.value.trim().toLowerCase() : '';

    const showOwnedCheckbox = document.getElementById('filter-roster-owned');
    const showUnownedCheckbox = document.getElementById('filter-roster-unowned');
    let showOwned = showOwnedCheckbox ? showOwnedCheckbox.checked : true;
    let showUnowned = showUnownedCheckbox ? showUnownedCheckbox.checked : true;
    if (!showOwned && !showUnowned) { showOwned = true; showUnowned = true; }

    const getChecked = (cls) => Array.from(document.querySelectorAll('.' + cls + ':checked')).map(cb => cb.getAttribute('data-filter-name').trim());
    const selectedRarities = getChecked('filter-rarity');
    const selectedTypes = getChecked('filter-type');
    const selectedAffinities = getChecked('filter-affinity');
    const selectedFactions = getChecked('filter-faction');
    const selectedAlliances = getChecked('filter-alliance');

    // Filter using cached data
    filteredCache = championCache.filter(item => {
        // Roster Owned/Unowned Logic
        const isOwned = item.el.classList.contains('selected');
        if (isOwned && !showOwned) return false;
        if (!isOwned && !showUnowned) return false;

        if (searchTerm && !item.name.includes(searchTerm)) return false;
        if (selectedRarities.length && !selectedRarities.includes(item.rarity)) return false;
        if (selectedTypes.length && !selectedTypes.includes(item.type)) return false;
        if (selectedAffinities.length && !selectedAffinities.includes(item.affinity)) return false;
        if (selectedFactions.length && !selectedFactions.includes(item.faction)) return false;
        if (selectedAlliances.length && !selectedAlliances.includes(item.alliance)) return false;
        return true;
    });

    const sortDropdown = document.getElementById('champion-sort');
    const sortMethod = sortDropdown ? sortDropdown.value : 'name_asc';

    filteredCache.sort((a, b) => {
        if (sortMethod === 'score_asc') return a.score - b.score;
        if (sortMethod === 'score_desc') return b.score - a.score;
        if (sortMethod === 'name_desc') return b.name.localeCompare(a.name);
        return a.name.localeCompare(b.name);
    });

    // Hide everything first
    championCache.forEach(c => c.el.style.display = 'none');

    // Update Counter
    const countElement = document.getElementById('visible-champion-count');
    if (countElement) {
        const currentCount = parseInt(countElement.innerText.replace(/,/g, '')) || 0;
        animateValue('visible-champion-count', currentCount, filteredCache.length, 400);
    }

    visibleCount = 0;
    renderNextBatch();

    const endTime = performance.now();
    console.log(`✅ Cache Filter Duration (${championCache.length} total): ${(endTime - startTime).toFixed(3)} ms`);
}

function animateValue(id, start, end, duration) {
    const obj = document.getElementById(id);
    if (!obj) return;
    if (start === end) { obj.innerText = end; return; }
    const startTime = performance.now();
    const step = (currentTime) => {
        const elapsed = currentTime - startTime;
        const progress = Math.min(elapsed / duration, 1);
        const current = Math.floor(progress * (end - start) + start);
        obj.innerText = current;
        if (progress < 1) window.requestAnimationFrame(step);
        else obj.innerText = end;
    };
    window.requestAnimationFrame(step);
}

function createStarHtml(score) {
    const rating = parseFloat(score);
    if (isNaN(rating) || rating < 0) return '';
    const fullStars = Math.floor(rating);
    const hasHalfStar = (rating % 1 !== 0);
    let starHtml = '';
    const starStyle = `style="width: 25px; height: 25px; vertical-align: top;"`;

    for (let i = 0; i < fullStars; i++) starHtml += `<img src="/images/star_full.png" ${starStyle}>`;
    if (hasHalfStar) {
        starHtml += `<div style="width: 13px; height: 25px; overflow: hidden; display: inline-block; vertical-align: top;"><img src="/images/star_full.png" ${starStyle}></div>`;
    }
    return `<div class="arena-rating" style="margin: 0px 0; white-space: nowrap;">${starHtml}</div>`;
}

function toggleAuraFields() {
    const checkbox = document.getElementById('auraExists');
    if (!checkbox) return;
    const statSelect = document.getElementById('stat');
    const locationSelect = document.getElementById('location');
    const amountInput = document.getElementById('amount');

    if (checkbox.checked) {
        document.getElementById('aura-fields-container').style.display = 'block';
        statSelect.required = true;
        locationSelect.required = true;
        amountInput.required = true;
        amountInput.min = 1;
    } else {
        document.getElementById('aura-fields-container').style.display = 'none';
        statSelect.required = false;
        locationSelect.required = false;
        amountInput.required = false;
        amountInput.removeAttribute('min');
    }
}

function toggleFilter() {
    const wrapper = document.getElementById('filter-wrapper');
    const arrow = document.getElementById('filter-arrow');
    const btnText = document.getElementById('filter-text');
    if (wrapper.style.maxHeight === '0px' || wrapper.style.maxHeight === '') {
        wrapper.style.maxHeight = '1000px';
        wrapper.style.opacity = '1';
        wrapper.style.padding = '20px';
        wrapper.style.marginBottom = '30px';
        wrapper.style.borderColor = '#ddd';
        arrow.textContent = '▲';
        btnText.textContent = 'Hide Filters & Sorting';
    } else {
        wrapper.style.maxHeight = '0px';
        wrapper.style.opacity = '0';
        wrapper.style.padding = '0 20px';
        wrapper.style.marginBottom = '0';
        wrapper.style.borderColor = 'transparent';
        arrow.textContent = '▼';
        btnText.textContent = 'Show Filters & Sorting';
    }
}

function initChampionDetails() {
    const container = document.getElementById('details-score-container');
    if (!container) return;
    const score = container.getAttribute('data-score');
    const starContainer = document.getElementById('details-star-rating');
    if (starContainer) starContainer.innerHTML = createStarHtml(score);
}

function setupBackToTop() {
    const backToTopBtn = document.getElementById("backToTopBtn");
    if (!backToTopBtn) return;
    window.addEventListener('scroll', () => {
        if (document.body.scrollTop > 300 || document.documentElement.scrollTop > 300) {
            backToTopBtn.style.display = "block";
        } else {
            backToTopBtn.style.display = "none";
        }
    });
    backToTopBtn.addEventListener("click", () => {
        window.scrollTo({ top: 0, behavior: "smooth" });
    });
}

function setupImageFadeIn() {
    const images = document.querySelectorAll('.champion-portrait');
    images.forEach(img => {
        const card = img.closest('.champion-card');
        const revealCard = () => { if (card) card.classList.remove('card-hidden'); };
        if (img.complete) revealCard();
        else { img.onload = revealCard; img.onerror = revealCard; }
    });
}

function renderNextBatch() {
    const container = document.getElementById('champion-grid-container');
    if (!container || filteredCache.length === 0) return;

    // Use a DocumentFragment for batch appending
    const fragment = document.createDocumentFragment();
    const nextBatch = filteredCache.slice(visibleCount, visibleCount + BATCH_SIZE);

    nextBatch.forEach(item => {
        item.el.style.display = '';
        fragment.appendChild(item.el); // Moves element to fragment

        // Image load check (same as before)
        const img = item.el.querySelector('.champion-portrait');
        if (img) {
            if (img.complete) item.el.classList.remove('card-hidden');
            else img.onload = () => item.el.classList.remove('card-hidden');
        }

        // Render Star rating if missing
        const score = item.score;
        const placeholder = item.el.querySelector('.champion-rating-placeholder');
        if (placeholder && placeholder.innerHTML === '') {
             placeholder.innerHTML = createStarHtml(score);
        }
    });

    container.appendChild(fragment); // Single append
    visibleCount += nextBatch.length;
}

function showLoading(message) {
    const overlay = document.getElementById('loading-overlay');
    const text = document.getElementById('loading-text');
    if (overlay) {
        text.innerText = message ? message : "Processing...";
        overlay.style.display = 'flex';
    }
}

function hideLoading() {
    const overlay = document.getElementById('loading-overlay');
    if (overlay) overlay.style.display = 'none';
}

function localizeDates() {
    const dateElements = document.querySelectorAll('.local-datetime');
    dateElements.forEach(el => {
        let isoDate = el.getAttribute('data-iso-date');
        if (!isoDate) return;
        if (!isoDate.endsWith('Z') && !isoDate.includes('+')) isoDate += 'Z';
        const date = new Date(isoDate);
        if (isNaN(date.getTime())) return;

        let localString = date.toLocaleString(undefined, {
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit'
        });
        // Replace separators with dashes
        localString = localString.replace(/\//g, '-').replace(/\./g, '-');
        el.textContent = localString;
        el.title = date.toString();
    });
}

function displayUserTimezone() {
    const timezoneElement = document.getElementById('user-timezone');
    if (!timezoneElement) return;
    try {
        const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
        timezoneElement.textContent = timezone;
    } catch (e) {
        console.warn("Could not detect timezone:", e);
        timezoneElement.textContent = 'Local Time';
    }
}

window.checkAll = (shouldCheck) => {
    const filterCheckboxes = document.querySelectorAll('.filter-checkbox');
    filterCheckboxes.forEach(checkbox => { checkbox.checked = shouldCheck; });
    applyChampionFilters();
};

window.addEventListener('pageshow', function(event) { hideLoading(); });

document.addEventListener('DOMContentLoaded', () => {
    // 1. Checkboxes
    const filterCheckboxes = document.querySelectorAll('.filter-checkbox');
    filterCheckboxes.forEach(checkbox => {
        checkbox.addEventListener('change', () => { applyChampionFilters(); });
    });

    // 2. Search Input (Input event for real-time filtering)
    const searchInput = document.getElementById('champion-search');
    if (searchInput) {
        searchInput.addEventListener('input', () => { applyChampionFilters(); });
    }

    // 3. Sort Select (Change event)
    const sortInput = document.getElementById('champion-sort');
    if (sortInput) {
        sortInput.addEventListener('change', () => { applyChampionFilters(); });
    }

    // 4. Infinite Scroll
    window.addEventListener('scroll', () => {
        if ((window.innerHeight + window.scrollY) >= document.body.offsetHeight - 200) {
            if (visibleCount < filteredCache.length) renderNextBatch();
        }
    });

    toggleAuraFields();
    setupStarListeners();
    initChampionDetails();
    setupBackToTop();
    setupImageFadeIn();
    localizeDates();
    displayUserTimezone();

    // Init the main cache and run filters once
    initChampionCache();
    window.checkAll(true);
});