import React, { useState } from "react";
import { Box, Card, Typography, LinearProgress, IconButton, Menu, MenuItem } from "@mui/material";
import MoreVertIcon from "@mui/icons-material/MoreVert";

const CategoryCard = ({ categoryName, total, spent, timeFrame, onEdit, onDelete }) => {
  const [anchorEl, setAnchorEl] = useState(null);
  const open = Boolean(anchorEl);
  const remaining = total - spent;
  const percentage = total > 0 ? (spent / total) * 100 : 0;

  const handleMenuClick = (e) => setAnchorEl(e.currentTarget);
  const handleClose = () => setAnchorEl(null);

  return (
    <Card sx={{ p: 2, mt: 2 }}>
      <Box display="flex" justifyContent="space-between" alignItems="center">
        <Typography variant="h6" fontWeight="bold" color="#37513D">
          {categoryName}
        </Typography>
        <IconButton onClick={handleMenuClick} size="small">
          <MoreVertIcon fontSize="small" />
        </IconButton>
        <Menu anchorEl={anchorEl} open={open} onClose={handleClose}>
          <MenuItem
            onClick={() => {
              onEdit();
              handleClose();
            }}
          >
            Edit
          </MenuItem>
          <MenuItem
            onClick={() => {
              onDelete();
              handleClose();
            }}
          >
            Delete
          </MenuItem>
        </Menu>
      </Box>

      <Typography variant="body2" color="textSecondary">
        {timeFrame}
      </Typography>

      <Box mt={1}>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography variant="body2" color="textSecondary">
            ${spent.toLocaleString()} of ${total.toLocaleString()}
          </Typography>
          <Typography variant="body2" fontWeight="bold" color={remaining < 0 ? "error" : "#37513D"}>
            {/* ${remaining.toLocaleString()} Left */}
            {remaining < 0 ? `Limit Exceeded by $${Math.abs(remaining).toLocaleString()}` : `$${remaining.toLocaleString()} Left`}
          </Typography>
        </Box>
        <LinearProgress
          variant="determinate"
          value={Math.min(percentage, 100)}
          sx={{ height: 8, borderRadius: 4, my: 1, backgroundColor: "#D9D9D9", "& .MuiLinearProgress-bar": { backgroundColor: percentage > 100 ? "#D32F2F" : "#37513D" } }}
        />
      </Box>

      {/* <Typography variant="body2" color="#37513D" mt={1} sx={{ cursor: "pointer" }}>
        View transactions
      </Typography> */}
    </Card>
  );
};

export default CategoryCard;
