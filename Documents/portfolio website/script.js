document.addEventListener('DOMContentLoaded', () => {
    // Mobile navigation toggle
    const hamburger = document.querySelector('.hamburger');
    const nav = document.querySelector('.nav');

    if (hamburger && nav) {
        hamburger.addEventListener('click', () => {
            nav.classList.toggle('active');
            hamburger.classList.toggle('active');
        });

        // Close nav when a link is clicked
        document.querySelectorAll('.nav-link').forEach(link => {
            link.addEventListener('click', () => {
                nav.classList.remove('active');
                hamburger.classList.remove('active');
            });
        });
    }

    // Scroll reveal effect with enhanced options
    const sections = document.querySelectorAll('.section');

    const observerOptions = {
        root: null,
        rootMargin: '0px',
        threshold: 0.1
    };

    const observer = new IntersectionObserver((entries, observer) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('active');
                
                // Add staggered animation for child elements
                const animatedElements = entry.target.querySelectorAll('.skill-progress, .project-card, .achievement-card, .feature-card, .review-card');
                animatedElements.forEach((el, index) => {
                    setTimeout(() => {
                        el.style.opacity = '1';
                        el.style.transform = 'translateY(0)';
                    }, index * 100);
                });
                
                observer.unobserve(entry.target);
            }
        });
    }, observerOptions);

    sections.forEach(section => {
        observer.observe(section);
    });

    // Enhanced starfield animation
    const canvas = document.getElementById('starfield-canvas');
    if (canvas) {
        const ctx = canvas.getContext('2d');

        let stars = [];
        const numStars = 500;

        function resizeCanvas() {
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
        }

        function initStars() {
            stars = [];
            for (let i = 0; i < numStars; i++) {
                stars.push({
                    x: Math.random() * canvas.width,
                    y: Math.random() * canvas.height,
                    size: Math.random() * 2,
                    speed: Math.random() * 0.5 + 0.1,
                    opacity: Math.random(),
                    twinkle: Math.random() * Math.PI * 2
                });
            }
        }

        function drawStars() {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            ctx.fillStyle = 'white';
            stars.forEach(star => {
                ctx.beginPath();
                ctx.arc(star.x, star.y, star.size, 0, Math.PI * 2);
                ctx.globalAlpha = star.opacity * (0.5 + 0.5 * Math.sin(star.twinkle));
                ctx.fill();
            });
        }

        function animateStars() {
            stars.forEach(star => {
                star.y += star.speed;
                star.twinkle += 0.02;
                if (star.y > canvas.height) {
                    star.y = 0;
                    star.x = Math.random() * canvas.width;
                }
            });
            drawStars();
            requestAnimationFrame(animateStars);
        }

        window.addEventListener('resize', () => {
            resizeCanvas();
            initStars();
        });

        resizeCanvas();
        initStars();
        animateStars();
    }

    // Skill bar animations
    const skillBars = document.querySelectorAll('.skill-progress');
    skillBars.forEach(bar => {
        bar.style.opacity = '0';
        bar.style.transform = 'translateY(20px)';
    });

    // Project card hover effects
    const projectCards = document.querySelectorAll('.project-card');
    projectCards.forEach(card => {
        card.addEventListener('mouseenter', () => {
            card.style.transform = 'translateY(-15px) scale(1.02)';
        });
        
        card.addEventListener('mouseleave', () => {
            card.style.transform = 'translateY(0) scale(1)';
        });
    });

    // GitHub stats counter animation
    const statNumbers = document.querySelectorAll('.stat-number');
    
    function animateCounter(element, target, duration = 2000) {
        const start = 0;
        const increment = target / (duration / 16);
        let current = start;
        
        const timer = setInterval(() => {
            current += increment;
            if (current >= target) {
                current = target;
                clearInterval(timer);
            }
            element.textContent = Math.floor(current) + (element.textContent.includes('+') ? '+' : '');
        }, 16);
    }

    // Trigger counter animation when elements come into view
    const counterObserver = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const target = parseInt(entry.target.textContent.replace(/\D/g, ''));
                animateCounter(entry.target, target);
                counterObserver.unobserve(entry.target);
            }
        });
    }, { threshold: 0.5 });

    statNumbers.forEach(stat => {
        counterObserver.observe(stat);
    });

    // Smooth scrolling for navigation links
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function (e) {
            e.preventDefault();
            const target = document.querySelector(this.getAttribute('href'));
            if (target) {
                target.scrollIntoView({
                    behavior: 'smooth',
                    block: 'start'
                });
            }
        });
    });

    // Header background effect on scroll
    const header = document.querySelector('.header');
    if (header) {
        window.addEventListener('scroll', () => {
            if (window.scrollY > 100) {
                header.style.background = 'rgba(10, 10, 10, 0.98)';
                header.style.backdropFilter = 'blur(20px)';
            } else {
                header.style.background = 'rgba(10, 10, 10, 0.95)';
                header.style.backdropFilter = 'blur(10px)';
            }
        });
    }

    // Floating card animation for app preview
    const floatingCard = document.querySelector('.floating-card');
    if (floatingCard) {
        let time = 0;
        function animateFloat() {
            time += 0.01;
            const y = Math.sin(time) * 10;
            floatingCard.style.transform = `translateY(${y}px)`;
            requestAnimationFrame(animateFloat);
        }
        animateFloat();
    }

    // Phone mockup interaction
    const phoneMockup = document.querySelector('.phone-mockup');
    if (phoneMockup) {
        phoneMockup.addEventListener('mousemove', (e) => {
            const rect = phoneMockup.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;
            
            const centerX = rect.width / 2;
            const centerY = rect.height / 2;
            
            const rotateX = (y - centerY) / 20;
            const rotateY = (centerX - x) / 20;
            
            phoneMockup.style.transform = `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg)`;
        });
        
        phoneMockup.addEventListener('mouseleave', () => {
            phoneMockup.style.transform = 'perspective(1000px) rotateX(0deg) rotateY(0deg)';
        });
    }

    // APK Download functionality
    const apkDownloadButtons = document.querySelectorAll('#download-apk, #download-apk-main');
    apkDownloadButtons.forEach(button => {
        button.addEventListener('click', (e) => {
            e.preventDefault();
            // Show placeholder message until APK is provided
            alert('APK download will be available soon! Please check back later or visit the GitHub repository for the source code.');
        });
    });

    // Download button interactions
    const downloadButtons = document.querySelectorAll('.download-btn, .download-option');
    downloadButtons.forEach(button => {
        button.addEventListener('click', (e) => {
            // Create ripple effect
            const ripple = document.createElement('span');
            const rect = button.getBoundingClientRect();
            const size = Math.max(rect.width, rect.height);
            const x = e.clientX - rect.left - size / 2;
            const y = e.clientY - rect.top - size / 2;
            
            ripple.style.width = ripple.style.height = size + 'px';
            ripple.style.left = x + 'px';
            ripple.style.top = y + 'px';
            ripple.classList.add('ripple');
            
            button.appendChild(ripple);
            
            setTimeout(() => {
                ripple.remove();
            }, 600);
        });
    });

    // Add ripple effect CSS
    const style = document.createElement('style');
    style.textContent = `
        .download-btn, .download-option {
            position: relative;
            overflow: hidden;
        }
        
        .ripple {
            position: absolute;
            border-radius: 50%;
            background: rgba(255, 255, 255, 0.3);
            transform: scale(0);
            animation: ripple-animation 0.6s linear;
            pointer-events: none;
        }
        
        @keyframes ripple-animation {
            to {
                transform: scale(4);
                opacity: 0;
            }
        }
    `;
    document.head.appendChild(style);

    // Parallax effect for hero section
    const heroSection = document.querySelector('.hero');
    if (heroSection) {
        window.addEventListener('scroll', () => {
            const scrolled = window.pageYOffset;
            const rate = scrolled * -0.5;
            heroSection.style.transform = `translateY(${rate}px)`;
        });
    }

    // Typing effect for hero title
    const heroName = document.querySelector('.hero-name');
    if (heroName) {
        const text = heroName.textContent;
        heroName.textContent = '';
        let i = 0;
        
        function typeWriter() {
            if (i < text.length) {
                heroName.textContent += text.charAt(i);
                i++;
                setTimeout(typeWriter, 100);
            }
        }
        
        // Start typing effect when page loads
        setTimeout(typeWriter, 1000);
    }

    // App screenshot carousel (if on app page)
    const screenshotItems = document.querySelectorAll('.screenshot-item');
    if (screenshotItems.length > 0) {
        let currentIndex = 0;
        
        function showScreenshot(index) {
            screenshotItems.forEach((item, i) => {
                item.style.opacity = i === index ? '1' : '0.5';
                item.style.transform = i === index ? 'scale(1.05)' : 'scale(1)';
            });
        }
        
        // Auto-rotate screenshots
        setInterval(() => {
            currentIndex = (currentIndex + 1) % screenshotItems.length;
            showScreenshot(currentIndex);
        }, 3000);
        
        // Initialize
        showScreenshot(0);
    }

    // Working Contact Form with EmailJS
    const contactForm = document.querySelector('.contact-form');
    if (contactForm) {
        // Load EmailJS
        const script = document.createElement('script');
        script.src = 'https://cdn.jsdelivr.net/npm/@emailjs/browser@3/dist/email.min.js';
        document.head.appendChild(script);

        script.onload = function() {
            // Initialize EmailJS with your public key
            emailjs.init('NhxAtbKwRPj_8y52D');
            
            contactForm.addEventListener('submit', (e) => {
                e.preventDefault();
                
                // Add loading state
                const submitBtn = contactForm.querySelector('.submit-button');
                const originalText = submitBtn.innerHTML;
                submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Sending...';
                submitBtn.disabled = true;
                
                // Get form data
                const formData = new FormData(contactForm);
                const name = formData.get('name');
                const email = formData.get('email');
                const message = formData.get('message');
                
                // EmailJS template parameters
                const templateParams = {
                    from_name: name,
                    from_email: email,
                    message: message,
                    to_name: 'Salman Malvasi'
                };
                
                // Send email using EmailJS
                console.log('Sending email with params:', templateParams);
                console.log('Service ID: service_nzg5tkc');
                console.log('Template ID: template_2pf8k7k');
                
                // Try EmailJS first
                try {
                    emailjs.send('service_nzg5tkc', 'template_2pf8k7k', templateParams)
                    .then(function(response) {
                        console.log('SUCCESS!', response.status, response.text);
                        submitBtn.innerHTML = '<i class="fas fa-check"></i> Sent Successfully!';
                        submitBtn.style.background = 'var(--success-color)';
                        
                        // Reset form
                        contactForm.reset();
                        
                        // Show success message
                        showNotification('Message sent successfully!', 'success');
                        
                        setTimeout(() => {
                            submitBtn.innerHTML = originalText;
                            submitBtn.disabled = false;
                            submitBtn.style.background = '';
                        }, 3000);
                    }, function(error) {
                        console.log('FAILED...', error);
                        console.log('Error details:', error.text);
                        
                        // Check if EmailJS is configured properly, if not use fallback
                        if (error.text && (error.text.includes('template') || error.text.includes('service'))) {
                            showNotification('EmailJS service needs configuration. Using email fallback.', 'error');
                        } else {
                            showNotification('Email service temporarily unavailable. Using fallback.', 'error');
                        }
                        
                        const subject = encodeURIComponent('Portfolio Contact from ' + name);
                        const body = encodeURIComponent(`Name: ${name}\nEmail: ${email}\nMessage: ${message}`);
                        window.open(`mailto:salmanmalvasi3@gmail.com?subject=${subject}&body=${body}`);
                        
                        showNotification('Email client opened. Please send manually.', 'success');
                        
                        submitBtn.innerHTML = '<i class="fas fa-check"></i> Email Client Opened';
                        submitBtn.style.background = 'var(--success-color)';
                        
                        setTimeout(() => {
                            submitBtn.innerHTML = originalText;
                            submitBtn.disabled = false;
                            submitBtn.style.background = '';
                        }, 3000);
                    });
                } catch (e) {
                    console.log('EmailJS not available, using fallback');
                    const subject = encodeURIComponent('Portfolio Contact from ' + name);
                    const body = encodeURIComponent(`Name: ${name}\nEmail: ${email}\nMessage: ${message}`);
                    window.open(`mailto:salmanmalvasi3@gmail.com?subject=${subject}&body=${body}`);
                    
                    showNotification('Opened email client. Please send manually.', 'success');
                    
                    submitBtn.innerHTML = '<i class="fas fa-check"></i> Email Client Opened';
                    submitBtn.style.background = 'var(--success-color)';
                    
                    setTimeout(() => {
                        submitBtn.innerHTML = originalText;
                        submitBtn.disabled = false;
                        submitBtn.style.background = '';
                    }, 3000);
                }
            });
        };
    }

    // Notification system
    function showNotification(message, type) {
        const notification = document.createElement('div');
        notification.className = `notification ${type}`;
        notification.innerHTML = `
            <i class="fas fa-${type === 'success' ? 'check-circle' : 'exclamation-circle'}"></i>
            <span>${message}</span>
        `;
        
        // Add notification styles
        const notificationStyle = document.createElement('style');
        notificationStyle.textContent = `
            .notification {
                position: fixed;
                top: 20px;
                right: 20px;
                padding: 1rem 2rem;
                border-radius: 10px;
                color: white;
                font-weight: 600;
                z-index: 1000;
                display: flex;
                align-items: center;
                gap: 0.5rem;
                animation: slideIn 0.3s ease-out;
            }
            
            .notification.success {
                background: var(--success-color);
            }
            
            .notification.error {
                background: var(--danger-color);
            }
            
            @keyframes slideIn {
                from {
                    transform: translateX(100%);
                    opacity: 0;
                }
                to {
                    transform: translateX(0);
                    opacity: 1;
                }
            }
        `;
        document.head.appendChild(notificationStyle);
        
        document.body.appendChild(notification);
        
        // Remove notification after 5 seconds
        setTimeout(() => {
            notification.style.animation = 'slideOut 0.3s ease-in';
            setTimeout(() => {
                document.body.removeChild(notification);
            }, 300);
        }, 5000);
    }

    // Initialize all animations
    function initAnimations() {
        // Add entrance animations to elements
        const animatedElements = document.querySelectorAll('.project-card, .achievement-card, .feature-card, .review-card');
        animatedElements.forEach((el, index) => {
            el.style.opacity = '0';
            el.style.transform = 'translateY(30px)';
            el.style.transition = 'opacity 0.6s ease, transform 0.6s ease';
        });
    }

    // Call initialization
    initAnimations();
});