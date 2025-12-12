function setupStarListeners() {
    const ratingContainer = document.getElementById('arena-score-rating');
    const finalInput = document.getElementById('finalArenaScore');

    if (!ratingContainer || !finalInput) return;

    ratingContainer.addEventListener('mousemove', (event) => {
        const hoveredLabel = event.target;

        ratingContainer.querySelectorAll('label').forEach(lbl => {
            lbl.classList.remove('half-star-hover');

            const originalTitle = lbl.getAttribute('data-original-title');
            if (originalTitle) {
                lbl.setAttribute('title', originalTitle);
            }
        });

        if (hoveredLabel.tagName !== 'LABEL') {
            return;
        }

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

        } else {
        }
    });

    ratingContainer.addEventListener('mouseenter', () => {
        ratingContainer.classList.remove('half-score-visual');
        ratingContainer.querySelectorAll('label').forEach(lbl => {
            lbl.classList.remove('checked-star');
            const originalTitle = lbl.getAttribute('data-original-title');
            if (originalTitle) {
                lbl.setAttribute('title', originalTitle);
            }
        });
    });

    // On leaving, clear all hover effects and restore the actual score visuals
    ratingContainer.addEventListener('mouseleave', () => {
        ratingContainer.querySelectorAll('label').forEach(lbl => lbl.classList.remove('half-star-hover'));

        const currentScore = parseFloat(finalInput.value);
        if (!isNaN(currentScore)) {
            updateVisuals(currentScore);
        }
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

        // Use clickEvent.clientX (or event.clientX if passed)
        // Since the event variable isn't explicitly passed in your HTML,
        // we must assume 'event' is the global event object available in the inline onclick:
        const clickX = event.clientX - rect.left;

        // CRITICAL FIX: Base the final score on the observed target value.
        if (clickX < rect.width / 2) {
            // Clicked left side (Targeting X.5): The desired score is 0.5 less than the label's value.
            // e.g., Clicked 3.0 label -> finalScore = 2.5
            finalScore = fullScore - 0.5;
        } else {
            // Clicked right side (Targeting X.0): The desired score is the label's value.
            // e.g., Clicked 3.0 label -> finalScore = 3.0
            finalScore = fullScore;
        }
    }

    // Ensure the score is still within bounds (1.0 to 5.0)
    finalScore = Math.max(1.0, Math.min(5.0, finalScore));
    finalInput.value = finalScore.toFixed(1);

    updateVisuals(finalScore);
}

function updateVisuals(score) {
    const ratingContainer = document.getElementById('arena-score-rating');
    const labels = ratingContainer ? ratingContainer.querySelectorAll('label') : [];

    if (!ratingContainer || labels.length === 0) return;

    let integerScore = Math.floor(score);

    if (score % 1 !== 0) {
        integerScore += 1;
    }

    const anchorId = 'score-' + (integerScore * 10).toFixed(0);
    const anchorLabel = document.querySelector(`label[for="${anchorId}"]`);

    labels.forEach(lbl => lbl.classList.remove('checked-star'));

    if (anchorLabel) {
        anchorLabel.classList.add('checked-star');
    }

    ratingContainer.classList.remove('half-score-visual');
    if (score % 1 !== 0) {
        ratingContainer.classList.add('half-score-visual');
    }
}

function applyChampionFilters() {
    const startTime = performance.now();

    const searchInput = document.getElementById('champion-search');
    const searchTerm = searchInput ? searchInput.value.trim().toLowerCase() : '';

    const selectedRarities = Array.from(document.querySelectorAll('.filter-rarity:checked')).map(cb => cb.getAttribute('data-filter-name').trim());
    const selectedTypes = Array.from(document.querySelectorAll('.filter-type:checked')).map(cb => cb.getAttribute('data-filter-name').trim());
    const selectedAffinities = Array.from(document.querySelectorAll('.filter-affinity:checked')).map(cb => cb.getAttribute('data-filter-name').trim());
    const selectedFactions = Array.from(document.querySelectorAll('.filter-faction:checked')).map(cb => cb.getAttribute('data-filter-name').trim());
    const selectedAlliances = Array.from(document.querySelectorAll('.filter-alliance:checked')).map(cb => cb.getAttribute('data-filter-name').trim());

    const totalRarities = document.querySelectorAll('.filter-rarity').length;
    const totalTypes = document.querySelectorAll('.filter-type').length;
    const totalAffinities = document.querySelectorAll('.filter-affinity').length;
    const totalFactions = document.querySelectorAll('.filter-faction').length;
    const totalAlliances = document.querySelectorAll('.filter-alliance').length;

    const championCards = document.querySelectorAll('.champion-card');

    // Array to hold all champions that pass ALL filters/search criteria
    let allPassedCards = [];

    // --- PHASE 1: FILTERING ---
    championCards.forEach(card => {
        const rarity = card.getAttribute('data-rarity').trim();
        const type = card.getAttribute('data-type').trim();
        const affinity = card.getAttribute('data-affinity').trim();
        const faction = card.getAttribute('data-faction').trim();
        const alliance = card.getAttribute('data-alliance').trim();

        let passesFilters = true; // Local variable inside the loop

        // Checkbox Filters
        // Helper function to check if filtering should be skipped for a category
        const shouldSkipFilter = (selectedArray) => {
            // Skips if the array is empty OR if all are selected (if you had the total logic)
            // For now, only check if it is empty, as this is the failing state (Clear All)
            return selectedArray.length === 0;
        }

        // Rarity Filter
        if (!shouldSkipFilter(selectedRarities) && !selectedRarities.includes(rarity)) {
            passesFilters = false;
        }

        // Type Filter
        if (passesFilters && !shouldSkipFilter(selectedTypes) && !selectedTypes.includes(type)) {
            passesFilters = false;
        }

        // Affinity Filter
        if (passesFilters && !shouldSkipFilter(selectedAffinities) && !selectedAffinities.includes(affinity)) {
            passesFilters = false;
        }

        // Faction Filter
        if (passesFilters && !shouldSkipFilter(selectedFactions) && !selectedFactions.includes(faction)) {
            passesFilters = false;
        }

        // Alliance Filter
        if (passesFilters && !shouldSkipFilter(selectedAlliances) && !selectedAlliances.includes(alliance)) {
            passesFilters = false;
        }

        // Search Filter (No change)
        if (passesFilters && searchTerm.length > 0) {
            const championName = card.getAttribute('data-name').toLowerCase();
            if (!championName.includes(searchTerm)) {
                passesFilters = false;
            }
        }

        // Search Filter
        if (passesFilters && searchTerm.length > 0) {
            const championName = card.getAttribute('data-name').toLowerCase();
            if (!championName.includes(searchTerm)) {
                passesFilters = false;
            }
        }

        if (passesFilters) {
            allPassedCards.push(card);
        }
    });

    const sortDropdown = document.getElementById('champion-sort');
    const sortMethod = sortDropdown ? sortDropdown.value : 'name_asc';

    allPassedCards.sort((a, b) => {
        const nameA = a.getAttribute('data-name').trim().toUpperCase();
        const nameB = b.getAttribute('data-name').trim().toUpperCase();
        const scoreA = parseFloat(a.getAttribute('data-score'));
        const scoreB = parseFloat(b.getAttribute('data-score'));

        switch (sortMethod) {
            case 'score_asc': return scoreA - scoreB;
            case 'score_desc': return scoreB - scoreA;
            case 'name_desc': return (nameA > nameB) ? -1 : (nameA < nameB) ? 1 : 0;
            case 'name_asc': default: return (nameA < nameB) ? -1 : (nameA > nameB) ? 1 : 0;
        }
    });

    const container = document.querySelector('.container section:nth-child(2) > div');
    if (container) {
        allPassedCards.forEach(card => {
            container.appendChild(card);
        });
    }

    const visibleCards = [];

    championCards.forEach(card => {
        card.style.display = 'none';
    });

    allPassedCards.forEach((card) => {
        card.style.display = ''; // Show all
        visibleCards.push(card);
    });

    visibleCards.forEach(card => {
        const score = card.getAttribute('data-score');
        const placeholder = card.querySelector('.champion-rating-placeholder');
        if (placeholder) {
            placeholder.innerHTML = createStarHtml(score);
        }
    });

    const endTime = performance.now();
    const duration = (endTime - startTime).toFixed(3);
    console.log(`✅ Filter/Sort Logic Duration (${championCards.length} total, ${allPassedCards.length} displayed): ${duration} ms`);
}

function createStarHtml(score) {
    const rating = parseFloat(score);
    if (isNaN(rating) || rating < 0) return '';

    const fullStars = Math.floor(rating);
    const hasHalfStar = (rating % 1 !== 0);

    let starHtml = '';
    const starSize = '25px';
    const halfWidth = '13px';
    const starStyle = `style="width: ${starSize}; height: ${starSize}; vertical-align: top;"`;

    for (let i = 0; i < fullStars; i++) {
        starHtml += `<img src="../images/star_full.png" ${starStyle}>`;
    }

    if (hasHalfStar) {
        const halfStarContainerStyle = `style="width: ${halfWidth}; height: ${starSize}; overflow: hidden; display: inline-block; vertical-align: top;"`;

        starHtml += `<div ${halfStarContainerStyle}>`;
        starHtml += `<img src="../images/star_full.png" ${starStyle}>`;
        starHtml += `</div>`;
    }

    return `<div class="arena-rating" style="margin: 0px 0; white-space: nowrap;">${starHtml}</div>`;
}

function toggleAuraFields() {
    const checkbox = document.getElementById('auraExists');
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

window.checkAll = (shouldCheck) => {
    const filterCheckboxes = document.querySelectorAll('.filter-checkbox');

    filterCheckboxes.forEach(checkbox => {
        checkbox.checked = shouldCheck;
    });

    applyChampionFilters();
};

document.addEventListener('DOMContentLoaded', () => {
    const filterCheckboxes = document.querySelectorAll('.filter-checkbox');

    filterCheckboxes.forEach(checkbox => {
        checkbox.addEventListener('change', () => {
            applyChampionFilters();
        });
    });

    applyChampionFilters();
    toggleAuraFields();
    setupStarListeners();

    window.checkAll(true);
});