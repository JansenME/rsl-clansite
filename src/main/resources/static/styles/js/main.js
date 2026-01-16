let allFilteredCards = [];
let visibleCount = 0;
const BATCH_SIZE = 24;

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

        const clickX = event.clientX - rect.left;

        if (clickX < rect.width / 2) {
            finalScore = fullScore - 0.5;
        } else {
            finalScore = fullScore;
        }
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

    const showOwnedCheckbox = document.getElementById('filter-roster-owned');
    const showUnownedCheckbox = document.getElementById('filter-roster-unowned');

    let showOwned = showOwnedCheckbox ? showOwnedCheckbox.checked : true;
    let showUnowned = showUnownedCheckbox ? showUnownedCheckbox.checked : true;

    if (!showOwned && !showUnowned) {
        showOwned = true;
        showUnowned = true;
    }

    const selectedRarities = Array.from(document.querySelectorAll('.filter-rarity:checked')).map(cb => cb.getAttribute('data-filter-name').trim());
    const selectedTypes = Array.from(document.querySelectorAll('.filter-type:checked')).map(cb => cb.getAttribute('data-filter-name').trim());
    const selectedAffinities = Array.from(document.querySelectorAll('.filter-affinity:checked')).map(cb => cb.getAttribute('data-filter-name').trim());
    const selectedFactions = Array.from(document.querySelectorAll('.filter-faction:checked')).map(cb => cb.getAttribute('data-filter-name').trim());
    const selectedAlliances = Array.from(document.querySelectorAll('.filter-alliance:checked')).map(cb => cb.getAttribute('data-filter-name').trim());

    const championCards = document.querySelectorAll('.champion-card');

    let allPassedCards = [];

    championCards.forEach(card => {
        const rarity = (card.getAttribute('data-rarity') || '').trim();
        const type = (card.getAttribute('data-type') || '').trim();
        const affinity = (card.getAttribute('data-affinity') || '').trim();
        const faction = (card.getAttribute('data-faction') || '').trim();
        const alliance = (card.getAttribute('data-alliance') || '').trim();

        let passesFilters = true;

        const isOwned = card.classList.contains('selected');

        if (isOwned && !showOwned) {
            passesFilters = false;
        }
        if (!isOwned && !showUnowned) {
            passesFilters = false;
        }

        const shouldSkipFilter = (selectedArray) => {
            return selectedArray.length === 0;
        }

        if (rarity !== '' && !shouldSkipFilter(selectedRarities) && !selectedRarities.includes(rarity)) {
            passesFilters = false;
        }

        if (passesFilters && type !== '' && !shouldSkipFilter(selectedTypes) && !selectedTypes.includes(type)) {
            passesFilters = false;
        }

        if (passesFilters && affinity !== '' && !shouldSkipFilter(selectedAffinities) && !selectedAffinities.includes(affinity)) {
            passesFilters = false;
        }

        if (passesFilters && faction !== '' && !shouldSkipFilter(selectedFactions) && !selectedFactions.includes(faction)) {
            passesFilters = false;
        }

        if (passesFilters && alliance !== '' && !shouldSkipFilter(selectedAlliances) && !selectedAlliances.includes(alliance)) {
            passesFilters = false;
        }

        if (passesFilters && searchTerm.length > 0) {
            const championName = (card.getAttribute('data-name') || '').toLowerCase();
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
        const nameA = (a.getAttribute('data-name') || '').trim().toUpperCase();
        const nameB = (b.getAttribute('data-name') || '').trim().toUpperCase();
        const scoreA = parseFloat(a.getAttribute('data-score')) || 0;
        const scoreB = parseFloat(b.getAttribute('data-score')) || 0;

        switch (sortMethod) {
            case 'score_asc': return scoreA - scoreB;
            case 'score_desc': return scoreB - scoreA;
            case 'name_desc': return (nameA > nameB) ? -1 : (nameA < nameB) ? 1 : 0;
            case 'name_asc': default: return (nameA < nameB) ? -1 : (nameA > nameB) ? 1 : 0;
        }
    });

    championCards.forEach(card => {
        card.style.display = 'none';
    });

    allFilteredCards = allPassedCards;

    const countElement = document.getElementById('visible-champion-count');
    if (countElement) {
        const currentCount = parseInt(countElement.innerText.replace(/,/g, '')) || 0;
        const newCount = allFilteredCards.length;
        animateValue('visible-champion-count', currentCount, newCount, 400);
    }

    visibleCount = 0;

    renderNextBatch();

    allFilteredCards.forEach(card => {
            const score = card.getAttribute('data-score');
            const placeholder = card.querySelector('.champion-rating-placeholder');
            if (placeholder && placeholder.innerHTML === '') {
                 placeholder.innerHTML = createStarHtml(score);
            }
        });

    const endTime = performance.now();
    const duration = (endTime - startTime).toFixed(3);
    console.log(`✅ Filter/Sort Logic Duration (${championCards.length} total, ${allPassedCards.length} displayed): ${duration} ms`);
}

function animateValue(id, start, end, duration) {
    const obj = document.getElementById(id);
    if (!obj) return;

    if (start === end) {
        obj.innerText = end;
        return;
    }

    const startTime = performance.now();

    const step = (currentTime) => {
        const elapsed = currentTime - startTime;
        const progress = Math.min(elapsed / duration, 1);

        const current = Math.floor(progress * (end - start) + start);
        obj.innerText = current;

        if (progress < 1) {
            window.requestAnimationFrame(step);
        } else {
            obj.innerText = end;
        }
    };

    window.requestAnimationFrame(step);
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
        starHtml += `<img src="/images/star_full.png" ${starStyle}>`;
    }

    if (hasHalfStar) {
        const halfStarContainerStyle = `style="width: ${halfWidth}; height: ${starSize}; overflow: hidden; display: inline-block; vertical-align: top;"`;

        starHtml += `<div ${halfStarContainerStyle}>`;
        starHtml += `<img src="/images/star_full.png" ${starStyle}>`;
        starHtml += `</div>`;
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

    if (starContainer) {
        starContainer.innerHTML = createStarHtml(score);
    }
}

function setupBackToTop() {
    const backToTopBtn = document.getElementById("backToTopBtn");

    if (!backToTopBtn) return; // Safety check if button is missing on other pages

    // 1. Show/Hide logic based on scroll position
    window.addEventListener('scroll', () => {
        if (document.body.scrollTop > 300 || document.documentElement.scrollTop > 300) {
            backToTopBtn.style.display = "block";
        } else {
            backToTopBtn.style.display = "none";
        }
    });

    // 2. Scroll to top logic
    backToTopBtn.addEventListener("click", () => {
        window.scrollTo({
            top: 0,
            behavior: "smooth"
        });
    });
}

function setupImageFadeIn() {
    const images = document.querySelectorAll('.champion-portrait');

    images.forEach(img => {
        const card = img.closest('.champion-card');

        // Function to reveal card
        const revealCard = () => {
            card.classList.remove('card-hidden');
        };

        // 1. If image is already cached/loaded, reveal immediately
        if (img.complete) {
            revealCard();
        } else {
            // 2. Otherwise, wait for load event
            img.onload = revealCard;

            // 3. Safety: If image fails (404), reveal anyway (showing placeholder)
            img.onerror = revealCard;
        }
    });
}

function renderNextBatch() {
    const container = document.getElementById('champion-grid-container');
    if (!container || allFilteredCards.length === 0) return;

    // Calculate the slice of cards to show
    const nextBatch = allFilteredCards.slice(visibleCount, visibleCount + BATCH_SIZE);

    nextBatch.forEach(card => {
        card.style.display = '';
        container.appendChild(card);

        // --- FIX STARTS HERE ---
        // Manually trigger the fade check for this specific card's image
        const img = card.querySelector('.champion-portrait');
        if (img) {
            if (img.complete) {
                // If already loaded, reveal immediately
                card.classList.remove('card-hidden');
            } else {
                // If not loaded yet, ensure the listener is there
                // (It should be from setupImageFadeIn, but re-attaching is safe)
                img.onload = () => card.classList.remove('card-hidden');
            }
        }
    });

    visibleCount += nextBatch.length;

    // Debug log
    console.log(`Shown ${visibleCount} / ${allFilteredCards.length} cards`);
}

function showLoading(message) {
    const overlay = document.getElementById('loading-overlay');
    const text = document.getElementById('loading-text');

    if (overlay) {
        if (message) {
            text.innerText = message;
        } else {
            text.innerText = "Processing..."; // Default text
        }
        overlay.style.display = 'flex'; // Show it
    }
}

function hideLoading() {
    const overlay = document.getElementById('loading-overlay');
    if (overlay) {
        overlay.style.display = 'none'; // Hide it
    }
}

window.checkAll = (shouldCheck) => {
    const filterCheckboxes = document.querySelectorAll('.filter-checkbox');

    filterCheckboxes.forEach(checkbox => {
        checkbox.checked = shouldCheck;
    });

    applyChampionFilters();
};

window.addEventListener('pageshow', function(event) {
    hideLoading();
});

document.addEventListener('DOMContentLoaded', () => {
    const filterCheckboxes = document.querySelectorAll('.filter-checkbox');

    filterCheckboxes.forEach(checkbox => {
        checkbox.addEventListener('change', () => {
            applyChampionFilters();
        });
    });

    window.addEventListener('scroll', () => {
        // Check if we are near the bottom of the page (within 200px)
        if ((window.innerHeight + window.scrollY) >= document.body.offsetHeight - 200) {
            // Only load more if there are more to load
            if (visibleCount < allFilteredCards.length) {
                renderNextBatch();
            }
        }
    });

    toggleAuraFields();
    setupStarListeners();
    initChampionDetails();
    setupBackToTop();
    setupImageFadeIn();

    window.checkAll(true);
});