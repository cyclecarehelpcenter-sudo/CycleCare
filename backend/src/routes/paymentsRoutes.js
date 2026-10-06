const express = require('express');
const router = express.Router();
const paymentsController = require('../controllers/paymentsController');
const authenticateToken = require('../middleware/auth');

router.post('/create', authenticateToken, paymentsController.createPaymentOrder);
router.post('/verify', authenticateToken, paymentsController.verifyPayment);
router.post('/webhook', paymentsController.handleWebhook);

module.exports = router;
