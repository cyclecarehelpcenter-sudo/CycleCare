const express = require('express');
const router = express.Router();
const adminController = require('../controllers/adminController');
const authenticateToken = require('../middleware/auth');
const authorizeRoles = require('../middleware/rbac');

// Protect all admin endpoints with JWT authentication and Role-Based Access Control (ADMIN or SUPER_ADMIN)
router.use(authenticateToken);
router.use(authorizeRoles('ADMIN', 'SUPER_ADMIN'));

router.get('/dashboard', adminController.getDashboardStats);
router.get('/users', adminController.getUsers);
router.patch('/users/:id/status', adminController.updateUserStatus);

router.get('/products', adminController.getAdminProducts);
router.post('/products', adminController.createProduct);
router.patch('/products/:id', adminController.updateProduct);

router.get('/orders', adminController.getAdminOrders);
router.patch('/orders/:id/status', adminController.updateOrderStatus);

router.get('/audit-logs', adminController.getAuditLogs);

module.exports = router;
