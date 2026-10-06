const express = require('express');
const router = express.Router();
const deliveryController = require('../controllers/deliveryController');
const authenticateToken = require('../middleware/auth');

router.get('/agent/dashboard', authenticateToken, deliveryController.getAgentDashboard);
router.get('/dashboard', authenticateToken, deliveryController.getAgentDashboard);
router.post('/:id/accept', authenticateToken, deliveryController.acceptDelivery);
router.post('/:id/pickup', authenticateToken, deliveryController.pickupDelivery);
router.post('/:id/start', authenticateToken, deliveryController.startDelivery);
router.post('/:id/arrived', authenticateToken, deliveryController.arrivedDelivery);
router.post('/:id/arrive', authenticateToken, deliveryController.arrivedDelivery);
router.post('/:id/complete', authenticateToken, deliveryController.completeDelivery);
router.post('/:id/location', authenticateToken, deliveryController.updateLocation);
router.post('/:id/simulate', authenticateToken, deliveryController.simulateLocation);
router.post('/demo/reset', authenticateToken, deliveryController.resetDemoDelivery);
router.get('/order/:id', authenticateToken, deliveryController.getOrderTracking);

module.exports = router;
