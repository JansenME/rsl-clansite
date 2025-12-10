function applyChampionFilters() {
    const startTime = performance.now();

    const searchInput = document.getElementById('champion-search');
    const searchTerm = searchInput ? searchInput.value.trim().toLowerCase() : '';

    // Filter selection variables (using 'data-filter-name' for consistency)
    const selectedRarities = Array.from(document.querySelectorAll('.filter-rarity:checked')).map(cb => cb.getAttribute('data-filter-name').trim());
    const selectedTypes = Array.from(document.querySelectorAll('.filter-type:checked')).map(cb => cb.getAttribute('data-filter-name').trim());
    const selectedAffinities = Array.from(document.querySelectorAll('.filter-affinity:checked')).map(cb => cb.getAttribute('data-filter-name').trim());
    const selectedFactions = Array.from(document.querySelectorAll('.filter-faction:checked')).map(cb => cb.getAttribute('data-filter-name').trim());
    // FIX applied: using 'data-filter-name' for alliance
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
        if (selectedRarities.length !== totalRarities && !selectedRarities.includes(rarity)) { passesFilters = false; }
        if (passesFilters && selectedTypes.length !== totalTypes && !selectedTypes.includes(type)) { passesFilters = false; }
        if (passesFilters && selectedAffinities.length !== totalAffinities && !selectedAffinities.includes(affinity)) { passesFilters = false; }
        if (passesFilters && selectedFactions.length !== totalFactions && !selectedFactions.includes(faction)) { passesFilters = false; }
        if (passesFilters && selectedAlliances.length !== totalAlliances && !selectedAlliances.includes(alliance)) { passesFilters = false; }

        // Search Filter
        if (passesFilters && searchTerm.length > 0) {
            const championName = card.getAttribute('data-name').toLowerCase();
            if (!championName.includes(searchTerm)) {
                passesFilters = false;
            }
        }

        // Collect ALL champions that passed
        if (passesFilters) {
            allPassedCards.push(card);
        }
    });

    // --- PHASE 2: SORTING & REORDERING ---
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
        // Re-insert ALL sorted cards into the DOM. This ensures they are in the correct order.
        allPassedCards.forEach(card => {
            container.appendChild(card);
        });
    }

    // --- PHASE 3: DISPLAY ALL & STYLING ---
    const visibleCards = []; // Now this array contains ALL filtered/sorted champions

    // 1. Hide ALL champions first (clean slate)
    championCards.forEach(card => {
        card.style.display = 'none';
    });

    // 2. Iterate through the sorted list and show ALL of them (no limit)
    allPassedCards.forEach((card) => {
        card.style.display = ''; // Show all
        visibleCards.push(card);
    });

    // 3. Inject Stars (run on the visible subset)
    visibleCards.forEach(card => {
        const score = card.getAttribute('data-score');
        const placeholder = card.querySelector('.champion-rating-placeholder');
        if (placeholder) {
            placeholder.innerHTML = createStarHtml(score);
        }
    });

    // Removed Sentinel Management

    const endTime = performance.now();
    const duration = (endTime - startTime).toFixed(3);
    console.log(`✅ Filter/Sort Logic Duration (${championCards.length} total, ${allPassedCards.length} displayed): ${duration} ms`);
}

// Removed resetDisplayLimit() function

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

window.checkAll = (shouldCheck) => {
    const filterCheckboxes = document.querySelectorAll('.filter-checkbox');

    filterCheckboxes.forEach(checkbox => {
        checkbox.checked = shouldCheck;
    });
    // Removed resetDisplayLimit()
    applyChampionFilters();
};

document.addEventListener('DOMContentLoaded', () => {
    const filterCheckboxes = document.querySelectorAll('.filter-checkbox');

    filterCheckboxes.forEach(checkbox => {
        checkbox.addEventListener('change', () => {
            // Removed resetDisplayLimit()
            applyChampionFilters();
        });
    });

    applyChampionFilters();

    // Removed Intersection Observer Setup
});